package com.qmz.canteen.service.impl;

import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.CustomerServiceRecordQuery;
import com.qmz.canteen.dto.KnowledgeQuery;
import com.qmz.canteen.mapper.CustomerServiceCenterMapper;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.CustomerServiceCenterService;
import com.qmz.canteen.vo.CustomerServiceRecordVO;
import com.qmz.canteen.vo.KnowledgeItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerServiceCenterServiceImpl implements CustomerServiceCenterService {

    private final CustomerServiceCenterMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<KnowledgeItemVO> knowledge(KnowledgeQuery query) {
        UserContext.requireAdmin();
        validatePage(query.getCurrent(), query.getSize());
        long total = mapper.countKnowledge(query);
        List<KnowledgeItemVO> records = total == 0 ? List.of()
                : mapper.selectKnowledge(query, ((long) query.getCurrent() - 1) * query.getSize());
        return page(records, total, query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CustomerServiceRecordVO> records(CustomerServiceRecordQuery query) {
        UserContext.requireAdmin();
        validatePage(query.getCurrent(), query.getSize());
        long total = mapper.countRecords(query);
        List<CustomerServiceRecordVO> records = total == 0 ? List.of()
                : mapper.selectRecords(query, ((long) query.getCurrent() - 1) * query.getSize());
        return page(records, total, query.getCurrent(), query.getSize());
    }

    private void validatePage(int current, int size) {
        if (current < 1 || size < 1 || size > 100) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "分页参数不合法");
        }
    }

    private <T> PageResult<T> page(List<T> records, long total, int current, int size) {
        PageResult<T> page = new PageResult<>();
        page.setRecords(records);
        page.setTotal(total);
        page.setCurrent((long) current);
        page.setSize((long) size);
        page.setPages(total / size + (total % size == 0 ? 0 : 1));
        return page;
    }
}
