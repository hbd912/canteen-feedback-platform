package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qmz.canteen.agent.EmbeddingClient;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.ExperienceRequest;
import com.qmz.canteen.entity.AiExperience;
import com.qmz.canteen.entity.AiExperienceHit;
import com.qmz.canteen.mapper.AiExperienceHitMapper;
import com.qmz.canteen.mapper.AiExperienceMapper;
import com.qmz.canteen.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 经验库 RAG：
 * <ul>
 *   <li>写入：人工验证过的问答/处理方式，先落库，向量可同步或异步补</li>
 *   <li>检索：问题向量化 → 应用层余弦相似度 → TopK；向量失败降级关键词</li>
 * </ul>
 * 不追求大规模，几百到几千条用内存计算足够，省掉向量数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceServiceImpl implements ExperienceService {

    private final AiExperienceMapper mapper;
    private final AiExperienceHitMapper hitMapper;
    private final EmbeddingClient embeddingClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double MIN_SCORE = 0.35;
    private static final int MAX_CANDIDATES = 200;

    @Override
    public void save(String scene, String problem, String solution, String outcome, Integer score,
                     String tags, Long stallId, String source, Long refId, boolean embedNow) {
        if (problem == null || problem.isBlank() || solution == null || solution.isBlank()) {
            return;
        }
        AiExperience exp = new AiExperience();
        exp.setScene(limit(scene, 60));
        exp.setProblem(limit(problem.trim(), 1000));
        exp.setSolution(limit(solution.trim(), 2000));
        exp.setOutcome(limit(outcome, 20));
        exp.setScore(score);
        exp.setTags(limit(tags, 200));
        exp.setStallId(stallId);
        exp.setSource(limit(source, 20));
        exp.setRefId(refId);
        mapper.insert(exp);
        if (embedNow) {
            embedOne(exp);
        }
    }

    @Override
    public List<ExperienceHit> retrieve(String scene, String query, Long stallId, int topK, boolean logHits) {
        List<ExperienceHit> hits = doRetrieve(scene, query, stallId, topK);
        if (logHits && !hits.isEmpty()) {
            persistHits(scene, query, hits);
        }
        return hits;
    }

    private List<ExperienceHit> doRetrieve(String scene, String query, Long stallId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int k = Math.min(Math.max(topK, 1), 10);
        LambdaQueryWrapper<AiExperience> base = new LambdaQueryWrapper<AiExperience>()
                .isNotNull(AiExperience::getEmbedding);
        if (scene != null && !scene.isBlank()) {
            base.eq(AiExperience::getScene, scene);
        }
        if (stallId != null) {
            base.and(w -> w.isNull(AiExperience::getStallId).or().eq(AiExperience::getStallId, stallId));
        }
        base.orderByDesc(AiExperience::getId).last("LIMIT " + MAX_CANDIDATES);
        List<AiExperience> candidates = mapper.selectList(base);
        if (candidates.isEmpty()) {
            return List.of();
        }

        float[] queryVector;
        try {
            queryVector = embeddingClient.embed(query);
        } catch (Exception e) {
            log.warn("经验检索向量化失败，降级关键词：{}", e.getMessage());
            return keywordFallback(scene, query, stallId, k);
        }

        List<ExperienceHit> hits = new ArrayList<>();
        for (AiExperience exp : candidates) {
            float[] vector = parseVector(exp.getEmbedding());
            if (vector == null) {
                continue;
            }
            double score = cosine(queryVector, vector);
            if (score >= MIN_SCORE) {
                hits.add(new ExperienceHit(exp, Math.round(score * 1000) / 1000.0));
            }
        }
        hits.sort(Comparator.comparingDouble(ExperienceHit::score).reversed());
        if (hits.isEmpty()) {
            return keywordFallback(scene, query, stallId, k);
        }
        return hits.size() <= k ? hits : hits.subList(0, k);
    }

    @Override
    public List<AiExperience> list(String scene, Long stallId, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        LambdaQueryWrapper<AiExperience> query = new LambdaQueryWrapper<>();
        if (scene != null && !scene.isBlank()) {
            query.eq(AiExperience::getScene, scene);
        }
        if (stallId != null) {
            query.eq(AiExperience::getStallId, stallId);
        }
        query.orderByDesc(AiExperience::getId).last("LIMIT " + size);
        return mapper.selectList(query);
    }

    @Override
    @Transactional
    public void update(Long id, ExperienceRequest request) {
        AiExperience existing = mapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "该经验不存在");
        }
        String scene = limit(request.getScene(), 60);
        String problem = limit(request.getProblem(), 1000);
        String solution = limit(request.getSolution(), 2000);
        String tags = limit(request.getTags(), 200);
        boolean contentChanged = !Objects.equals(existing.getScene(), scene)
                || !Objects.equals(existing.getProblem(), problem)
                || !Objects.equals(existing.getSolution(), solution)
                || !Objects.equals(existing.getTags(), tags);
        LambdaUpdateWrapper<AiExperience> update = new LambdaUpdateWrapper<AiExperience>()
                .eq(AiExperience::getId, id)
                .set(AiExperience::getScene, scene)
                .set(AiExperience::getProblem, problem)
                .set(AiExperience::getSolution, solution)
                .set(AiExperience::getTags, tags)
                .set(AiExperience::getScore, request.getScore())
                .set(AiExperience::getStallId, request.getStallId());
        if (contentChanged) {
            update.set(AiExperience::getEmbedding, null)
                    .set(AiExperience::getEmbeddingModel, null);
        }
        mapper.update(null, update);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public int embedPending(int limit) {
        List<AiExperience> pending = mapper.selectPendingEmbedding(Math.min(Math.max(limit, 1), 50));
        int count = 0;
        for (AiExperience exp : pending) {
            if (embedOne(exp)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public long count() {
        return mapper.selectCount(null);
    }

    @Override
    public long countEmbedded() {
        return mapper.countEmbedded();
    }

    /** 命中日志用于后台追溯，写失败不影响检索结果 */
    private void persistHits(String scene, String query, List<ExperienceHit> hits) {
        try {
            String q = query.length() <= 500 ? query : query.substring(0, 500);
            for (ExperienceHit hit : hits) {
                AiExperienceHit log = new AiExperienceHit();
                log.setScene(scene);
                log.setQueryText(q);
                log.setExperienceId(hit.experience().getId());
                log.setScore(BigDecimal.valueOf(hit.score()));
                hitMapper.insert(log);
            }
        } catch (Exception e) {
            log.warn("经验命中日志写入失败：{}", e.getMessage());
        }
    }

    private boolean embedOne(AiExperience exp) {        try {
            String text = "问题：" + exp.getProblem() + "\n解法：" + exp.getSolution();
            float[] vector = embeddingClient.embed(text);
            exp.setEmbedding(MAPPER.writeValueAsString(vector));
            exp.setEmbeddingModel(embeddingClient.model());
            mapper.updateById(exp);
            return true;
        } catch (Exception e) {
            log.warn("经验 {} 向量化失败：{}", exp.getId(), e.getMessage());
            return false;
        }
    }

    /** 向量不可用时的兜底：用问题前 30 字做 LIKE 匹配 */
    private List<ExperienceHit> keywordFallback(String scene, String query, Long stallId, int k) {
        String raw = query.trim();
        String kw = raw.length() > 30 ? raw.substring(0, 30) : raw;
        LambdaQueryWrapper<AiExperience> wrapper = new LambdaQueryWrapper<AiExperience>()
                .and(w -> w.like(AiExperience::getProblem, kw).or().like(AiExperience::getSolution, kw));
        if (scene != null && !scene.isBlank()) {
            wrapper.eq(AiExperience::getScene, scene);
        }
        if (stallId != null) {
            wrapper.and(w -> w.isNull(AiExperience::getStallId).or().eq(AiExperience::getStallId, stallId));
        }
        wrapper.orderByDesc(AiExperience::getId).last("LIMIT " + k);
        List<ExperienceHit> hits = new ArrayList<>();
        for (AiExperience exp : mapper.selectList(wrapper)) {
            hits.add(new ExperienceHit(exp, 0.0));
        }
        return hits;
    }

    private float[] parseVector(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, float[].class);
        } catch (Exception e) {
            return null;
        }
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String limit(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
