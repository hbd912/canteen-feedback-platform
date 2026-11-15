package com.qmz.canteen.service.impl;

import com.qmz.canteen.agent.MerchantReplyGenerator;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.entity.CustomerServiceMessage;
import com.qmz.canteen.entity.CustomerServiceSession;
import com.qmz.canteen.mapper.CustomerServiceMessageMapper;
import com.qmz.canteen.mapper.CustomerServiceSessionMapper;
import com.qmz.canteen.security.LoginUser;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.CustomerServiceStreamService;
import com.qmz.canteen.vo.CustomerServiceMessageVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceAdminMessagesTest {

    @Mock
    private CustomerServiceSessionMapper sessionMapper;
    @Mock
    private CustomerServiceMessageMapper messageMapper;
    @Mock
    private MerchantReplyGenerator replyGenerator;
    @Mock
    private CustomerServiceStreamService streamService;
    @InjectMocks
    private CustomerServiceServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUser admin = new LoginUser();
        admin.setUserId(100L);
        admin.setRole("ADMIN");
        UserContext.set(admin);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void adminReadMapsMessagesWithoutUpdatingUnreadOrTriggeringAi() {
        CustomerServiceSession conversation = new CustomerServiceSession();
        conversation.setId(1L);
        conversation.setMerchantUnread(3);
        conversation.setStudentUnread(2);
        CustomerServiceMessage message = new CustomerServiceMessage();
        message.setId(4L);
        message.setSessionId(1L);
        message.setSenderType("STUDENT");
        message.setSenderId(20L);
        message.setContent("原始消息");
        message.setCreateTime(LocalDateTime.of(2026, 11, 14, 10, 0));
        when(sessionMapper.selectById(1L)).thenReturn(conversation);
        when(messageMapper.listBySession(1L)).thenReturn(List.of(message));

        List<CustomerServiceMessageVO> result = service.adminGetMessages(1L);
        assertEquals(1, result.size());
        assertEquals(4L, result.get(0).getId());
        assertEquals("原始消息", result.get(0).getContent());
        assertEquals(message.getCreateTime(), result.get(0).getCreateTime());
        assertFalse(result.get(0).getSelf());
        assertEquals(3, conversation.getMerchantUnread());
        assertEquals(2, conversation.getStudentUnread());
        verify(sessionMapper).selectById(1L);
        verify(messageMapper).listBySession(1L);
        verifyNoMoreInteractions(sessionMapper, messageMapper);
        verifyNoInteractions(replyGenerator, streamService);
    }

    @Test
    void missingSessionIsRejectedBeforeLoadingMessages() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.adminGetMessages(7L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), exception.getCode());
        verifyNoInteractions(messageMapper, replyGenerator, streamService);
    }

    @Test
    void nonAdminCannotReadAnySessionThroughAdminEndpoint() {
        LoginUser student = new LoginUser();
        student.setUserId(20L);
        student.setRole("STUDENT");
        UserContext.set(student);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.adminGetMessages(1L));
        assertEquals(ResultCode.FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(sessionMapper, messageMapper, replyGenerator, streamService);
    }

    @Test
    void adminStillCannotUseOrdinaryMessageEndpoint() {
        when(sessionMapper.selectById(1L)).thenReturn(new CustomerServiceSession());
        BusinessException exception = assertThrows(BusinessException.class, () -> service.getMessages(1L));
        assertEquals(ResultCode.FORBIDDEN.getCode(), exception.getCode());
        verify(sessionMapper).selectById(1L);
        verifyNoMoreInteractions(sessionMapper);
        verifyNoInteractions(messageMapper, replyGenerator, streamService);
    }
}
