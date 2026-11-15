package com.qmz.canteen.service;

import com.qmz.canteen.dto.ExperienceRequest;
import com.qmz.canteen.entity.AiExperience;

import java.util.List;

public interface ExperienceService {

    /** 写入一条经验；embedNow=true 时同步向量化，false 交给定时任务 */
    void save(String scene, String problem, String solution, String outcome, Integer score,
              String tags, Long stallId, String source, Long refId, boolean embedNow);

    /** 检索相似经验（向量优先，失败降级关键词），并记录命中日志 */
    default List<ExperienceHit> retrieve(String scene, String query, Long stallId, int topK) {
        return retrieve(scene, query, stallId, topK, true);
    }

    /** @param logHits 是否写入命中日志（管理员检索测试时传 false，避免污染审计流） */
    List<ExperienceHit> retrieve(String scene, String query, Long stallId, int topK, boolean logHits);

    List<AiExperience> list(String scene, Long stallId, int limit);

    /** 原位编辑经验，保留来源；内容变化后清除旧向量，由后台任务补齐。 */
    void update(Long id, ExperienceRequest request);

    void delete(Long id);

    /** 给还没向量化的经验补向量，返回处理条数 */
    int embedPending(int limit);

    long count();

    long countEmbedded();

    record ExperienceHit(AiExperience experience, double score) {
    }
}
