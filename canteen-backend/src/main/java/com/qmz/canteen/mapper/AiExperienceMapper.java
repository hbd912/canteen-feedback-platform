package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qmz.canteen.entity.AiExperience;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AiExperienceMapper extends BaseMapper<AiExperience> {

    /** 取还没向量化的经验，交给定时任务补向量 */
    @Select("SELECT * FROM ai_experience WHERE deleted = 0 AND embedding IS NULL "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<AiExperience> selectPendingEmbedding(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ai_experience WHERE deleted = 0 AND embedding IS NOT NULL")
    long countEmbedded();
}
