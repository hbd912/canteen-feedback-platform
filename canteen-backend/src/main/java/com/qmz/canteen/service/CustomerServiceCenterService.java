package com.qmz.canteen.service;

import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.dto.CustomerServiceRecordQuery;
import com.qmz.canteen.dto.KnowledgeQuery;
import com.qmz.canteen.vo.CustomerServiceRecordVO;
import com.qmz.canteen.vo.KnowledgeItemVO;

public interface CustomerServiceCenterService {

    PageResult<KnowledgeItemVO> knowledge(KnowledgeQuery query);

    PageResult<CustomerServiceRecordVO> records(CustomerServiceRecordQuery query);
}
