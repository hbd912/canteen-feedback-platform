package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qmz.canteen.entity.User;
import com.qmz.canteen.vo.UserInfoVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface UserMapper extends BaseMapper<User> {

    /** 商家账号分页：账号信息 + 所属档口经营数据 */
    IPage<UserInfoVO> selectMerchantPage(IPage<UserInfoVO> page,
                                         @Param("keyword") String keyword,
                                         @Param("status") Integer status);

    /** 充值成功时给账户余额做原子累加 */
    @Update("UPDATE sys_user SET balance = IFNULL(balance, 0) + #{amount} WHERE id = #{userId}")
    int incrBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 下单时从余额原子扣款；余额不足时返回 0（带充足校验） */
    @Update("UPDATE sys_user SET balance = IFNULL(balance, 0) - #{amount} "
            + "WHERE id = #{userId} AND IFNULL(balance, 0) >= #{amount}")
    int decrBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
