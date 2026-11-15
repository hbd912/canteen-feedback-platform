package com.qmz.canteen.service;

import com.qmz.canteen.vo.NotificationVO;

import java.util.List;

public interface NotificationService {

    List<NotificationVO> list(Long userId, int limit);

    long countUnread(Long userId);

    void markAllRead(Long userId);

    void markRead(Long userId, Long id);

    /** 给单个用户发通知 */
    void notify(Long userId, String type, String content, String targetUrl, Long targetId);

    /** 给某档口的商家发通知（若该档口有商家账号） */
    void notifyMerchant(Long stallId, String type, String content, String targetUrl, Long targetId);

    /** 给所有管理员发通知 */
    void notifyAdmins(String type, String content, String targetUrl, Long targetId);
}
