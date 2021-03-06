package com.javazhan.core.service.impl;

import com.google.gson.Gson;
import com.javazhan.core.constant.Constants;
import com.javazhan.core.dao.mapper.TUserMapper;
import com.javazhan.core.model.dto.TUser;
import com.javazhan.core.model.dto.TUserExample;
import com.javazhan.core.model.pojo.JedisClient;
import com.javazhan.core.model.pojo.ResponseData;
import com.javazhan.core.service.UserSv;
import com.javazhan.utils.CheckUtil;
import com.javazhan.utils.Md5EncryptUtil;
import com.javazhan.utils.UUIDUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author: yandq
 * @Description:
 * @Date: Create in 9:34 2018/3/27
 * @Modified By:
 */
@Service("userSv")
public class UserSvImpl implements UserSv {

    @Autowired
    private TUserMapper userMapper;
    @Autowired
    private JedisClient jedisClient;
    @Value("${SESSION_EXPIRE}")
    private Integer SESSION_EXPIRE;

    @Override
    public ResponseData checkData(String param, int type) {
        TUserExample example = new TUserExample();
        TUserExample.Criteria criteria = example.createCriteria();
        // 根据查询条件动态生成 1、2、3 分别代表 username、phone、email
        if (type==Constants.DIGITAL_1) {
            criteria.andUserNameEqualTo(param);
        } else if (type==Constants.DIGITAL_2) {
            criteria.andPhoneEqualTo(param);
        } else if (type==Constants.DIGITAL_3) {
            criteria.andEmailEqualTo(param);
        } else {
            return ResponseData.init(Constants.DIGITAL_4444,"非法的参数",null);
        }

        List<TUser> list = userMapper.selectByExample(example);

        if (list==null||list.size()==0) {
            return ResponseData.init("9999","未查到任何信息",null);
        }
        return ResponseData.init(Constants.DIGITAL_0000,"success", new Gson().toJson(list.get(0)));
    }

    @Override
    public ResponseData insertUser(TUser user) {
        // 密码要进行 MD5 加密。
        String md5Pass = Md5EncryptUtil.encryptToMD5(user.getPassword());
        user.setPassword(md5Pass);
        user.setUserId(UUIDUtil.genIdToUpperCase());
        // 把用户信息插入到数据库中。
        userMapper.insert(user);
        // 返回
        return ResponseData.init(Constants.DIGITAL_0000,"success", null);
    }

    @Override
    public ResponseData login(String userName, String password) {
        TUserExample example = new TUserExample();
        TUserExample.Criteria criteria = example.createCriteria();
        // 根据查询条件动态生成 1、2、3 分别代表 username、phone、email
        //1、判断用户名密码是否正确。
        Boolean checkPhone = CheckUtil.checkPhone(userName);
        Boolean checkEmail = CheckUtil.checkEmail(userName);
        if (checkPhone) {
            criteria.andPhoneEqualTo(userName);
        }
        if (checkEmail) {
            criteria.andEmailEqualTo(userName);
        }
        if (!checkPhone&&!checkEmail) {
            criteria.andUserNameEqualTo(userName);
        }
        String md5Pass = Md5EncryptUtil.encryptToMD5(password);
        criteria.andPasswordEqualTo(md5Pass);
        List<TUser> list = userMapper.selectByExample(example);

        if (list==null||list.size()==0) {
            return ResponseData.init(Constants.DIGITAL_9999,"用户名或密码错误",null);
        }
        TUser user = list.get(0);
        //2、登录成功后生成 token。Token 相当于原来的 jsessionid，字符串，可以使用 uuid。
        String token = user.getUserId();
        //3、把用户信息保存到 redis。Key 就是 token，value 就是 TbUser 对象转换成 json。
        //4、使用 String 类型保存 Session 信息。可以使用“前缀:token”为 key
        user.setPassword(null);
        jedisClient.set("SESSION:"+token, new Gson().toJson(user));
        //5、设置 key 的过期时间。模拟 Session 的过期时间。一般半个小时。
        jedisClient.expire("SESSION:"+token, SESSION_EXPIRE);
        //6、返回 ResponseData 包装 token。
        return ResponseData.init(Constants.DIGITAL_0000,"sussess", token);
    }

    @Override
    public ResponseData getUserByToken(String token) {
        String redisUser = jedisClient.get("SESSION:"+token);
        if (StringUtils.isEmpty(redisUser)) {
            return ResponseData.init(Constants.DIGITAL_4444, "用户登录已经过期，请重新登录。", null);
        }
        return ResponseData.init(Constants.DIGITAL_0000, "用户登录成功", redisUser);
    }

    @Override
    public ResponseData logon(String token) {
        jedisClient.del("SESSION:"+token);
        return ResponseData.init(Constants.DIGITAL_0000, "用户注销成功", null);
    }
}
