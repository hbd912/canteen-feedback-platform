package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.ExperienceRequest;
import com.qmz.canteen.entity.AiExperience;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.ExperienceService;
import com.qmz.canteen.vo.ExperienceVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理员：AI 经验库（RAG）管理。可查看、检索、新增、删除、重建向量。
 */
@Validated
@RestController
@RequestMapping("/admin/experiences")
@RequiredArgsConstructor
public class AdminExperienceController {

    private final ExperienceService experienceService;

    @GetMapping
    public Result<List<ExperienceVO>> list(@RequestParam(required = false) String scene,
                                           @RequestParam(required = false) Long stallId,
                                           @RequestParam(defaultValue = "100") Integer limit) {
        UserContext.requireAdmin();
        List<ExperienceVO> result = new ArrayList<>();
        for (AiExperience exp : experienceService.list(scene, stallId, limit == null ? 100 : limit)) {
            result.add(toVO(exp, null));
        }
        return Result.success(result);
    }

    @GetMapping("/search")
    public Result<List<ExperienceVO>> search(@RequestParam String q,
                                             @RequestParam(required = false) String scene,
                                             @RequestParam(required = false) Long stallId,
                                             @RequestParam(defaultValue = "5") Integer topK) {
        UserContext.requireAdmin();
        List<ExperienceVO> result = new ArrayList<>();
        for (ExperienceService.ExperienceHit hit
                : experienceService.retrieve(scene, q, stallId, topK == null ? 5 : topK, false)) {
            result.add(toVO(hit.experience(), hit.score()));
        }
        return Result.success(result);
    }

    @PostMapping
    public Result<Void> create(@RequestBody @Valid ExperienceRequest request) {
        UserContext.requireAdmin();
        experienceService.save(request.getScene(), request.getProblem(), request.getSolution(),
                "SUCCESS", request.getScore(), request.getTags(), request.getStallId(),
                "ADMIN", null, true);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "ID不合法") Long id,
                               @RequestBody @Valid ExperienceRequest request) {
        UserContext.requireAdmin();
        experienceService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "ID不合法") Long id) {
        UserContext.requireAdmin();
        experienceService.delete(id);
        return Result.success();
    }

    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex() {
        UserContext.requireAdmin();
        int count = experienceService.embedPending(50);
        return Result.success(Map.of("embedded", count));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        UserContext.requireAdmin();
        long total = experienceService.count();
        long embedded = experienceService.countEmbedded();
        return Result.success(Map.of("total", total, "embedded", embedded, "pending", total - embedded));
    }

    private ExperienceVO toVO(AiExperience exp, Double matchScore) {
        ExperienceVO vo = new ExperienceVO();
        vo.setId(exp.getId());
        vo.setScene(exp.getScene());
        vo.setProblem(exp.getProblem());
        vo.setSolution(exp.getSolution());
        vo.setOutcome(exp.getOutcome());
        vo.setScore(exp.getScore());
        vo.setTags(exp.getTags());
        vo.setStallId(exp.getStallId());
        vo.setSource(exp.getSource());
        vo.setEmbeddingModel(exp.getEmbeddingModel());
        vo.setHasEmbedding(exp.getEmbedding() != null && !exp.getEmbedding().isBlank());
        vo.setCreateTime(exp.getCreateTime());
        vo.setMatchScore(matchScore);
        return vo;
    }
}
