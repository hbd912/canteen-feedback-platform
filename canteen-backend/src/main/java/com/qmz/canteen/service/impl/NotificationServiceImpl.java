package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.entity.Notification;
import com.qmz.canteen.entity.User;
import com.qmz.canteen.mapper.NotificationMapper;
import com.qmz.canteen.mapper.UserMapper;
import com.qmz.canteen.service.NotificationService;
import com.qmz.canteen.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    @Override
    public List<NotificationVO> list(Long userId, int limit) {
        int size = limit < 1 ? 20 : Math.min(limit, 100);
        return notificationMapper.listByUser(userId, size).stream().map(this::toVO).toList();
    }

    @Override
    public long countUnread(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }

    @Override
    public void markRead(Long userId, Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId) || Integer.valueOf(1).equals(n.getIsRead())) {
            return;
        }
        Notification upd = new Notification();
        upd.setId(id);
        upd.setIsRead(1);
        notificationMapper.updateById(upd);
    }

    @Override
    public void notify(Long userId, String type, String content, String targetUrl, Long targetId) {
        if (userId == null) {
            return;
        }
        insert(userId, type, content, targetUrl, targetId);
    }

    @Override
    public void notifyMerchant(Long stallId, String type, String content, String targetUrl, Long targetId) {
        if (stallId == null) {
            return;
        }
        List<User> merchants = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "MERCHANT")
                .eq(User::getStallId, stallId)
                .eq(User::getDeleted, 0));
        for (User m : merchants) {
            insert(m.getId(), type, content, targetUrl, targetId);
        }
    }

    @Override
    public void notifyAdmins(String type, String content, String targetUrl, Long targetId) {
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "ADMIN")
                .eq(User::getDeleted, 0));
        for (User a : admins) {
            insert(a.getId(), type, content, targetUrl, targetId);
        }
    }

    private void insert(Long userId, String type, String content, String targetUrl, Long targetId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setContent(content);
        n.setTargetUrl(targetUrl);
        n.setTargetId(targetId);
        n.setIsRead(0);
        try {
            notificationMapper.insert(n);
        } catch (Exception e) {
            // 通知写失败不影响主流程
            log.warn("通知写入失败：userId={} type={}", userId, type, e);
        }
    }

    private NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setReviewId(n.getReviewId());
        vo.setDishId(n.getDishId());
        vo.setReplyId(n.getReplyId());
        vo.setTargetUrl(n.getTargetUrl());
        vo.setTargetId(n.getTargetId());
        vo.setContent(n.getContent());
        vo.setIsRead(n.getIsRead());
        vo.setCreateTime(n.getCreateTime());
        return vo;
    }
}
