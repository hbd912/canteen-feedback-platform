package com.qmz.canteen.mapper;

import com.qmz.canteen.dto.CustomerServiceRecordQuery;
import com.qmz.canteen.dto.KnowledgeQuery;
import com.qmz.canteen.vo.CustomerServiceRecordVO;
import com.qmz.canteen.vo.KnowledgeItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 客服中心只读联合查询：在数据库中统一过滤、计数、排序和分页。 */
public interface CustomerServiceCenterMapper {

    List<KnowledgeItemVO> selectKnowledge(@Param("query") KnowledgeQuery query,
                                         @Param("offset") long offset);

    long countKnowledge(@Param("query") KnowledgeQuery query);

    List<CustomerServiceRecordVO> selectRecords(@Param("query") CustomerServiceRecordQuery query,
                                                @Param("offset") long offset);

    long countRecords(@Param("query") CustomerServiceRecordQuery query);
}
