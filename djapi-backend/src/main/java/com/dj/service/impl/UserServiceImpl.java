package com.dj.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dj.common.ErrorCode;
import com.dj.constant.CommonConstant;
import com.dj.exception.BusinessException;
import com.dj.mapper.UserMapper;
import com.dj.model.dto.user.UserBindEmailRequest;
import com.dj.model.dto.user.UserEmailLoginRequest;
import com.dj.model.dto.user.UserQueryRequest;
import com.dj.model.dto.user.UserUnBindEmailRequest;
import com.dj.model.enums.UserRoleEnum;
import com.dj.model.vo.LoginUserVO;
import com.dj.service.UserService;
import com.dj.utils.SqlUtils;
import com.djapicommon.model.emums.UserAccountStatusEnum;
import com.djapicommon.model.entity.User;
import com.djapicommon.model.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.dj.constant.UserConstant.USER_LOGIN_STATE;
import static com.dj.controller.UserController.CAPTCHA_CACHE_KEY;

/**
 * 用户服务实现
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "dj";

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            // 2. 加密
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            // 3. 分配 accessKey, secretKey
            String accessKey = DigestUtil.md5Hex(SALT + userAccount + RandomUtil.randomNumbers(5));
            String secretKey = DigestUtil.md5Hex(SALT + userAccount + RandomUtil.randomNumbers(8));
            // 4. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            user.setAccessKey(accessKey);
            user.setSecretKey(secretKey);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            return null;
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        return this.getById(userId);
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (request.getSession().getAttribute(USER_LOGIN_STATE) == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollectionUtils.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "unionId", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 用户电子邮件登录
     *
     * @param userEmailLoginRequest 用户电子邮件登录请求
     * @param request               要求
     * @return {@link UserVO}
     */
    @Override
    public UserVO userEmailLogin(UserEmailLoginRequest userEmailLoginRequest, HttpServletRequest request) {
        String emailAccount = userEmailLoginRequest.getEmailAccount();
        String captcha = userEmailLoginRequest.getCaptcha();

        if (StringUtils.isAnyBlank(emailAccount, captcha)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        if (!Pattern.matches(emailPattern, emailAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不合法的邮箱地址！");
        }
        String cacheCaptcha = redisTemplate.opsForValue().get(CAPTCHA_CACHE_KEY + emailAccount);
        //todo
//        cacheCaptcha="486714";
        if (StringUtils.isBlank(cacheCaptcha)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码已过期,请重新获取");
        }
        captcha = captcha.trim();
        if (!cacheCaptcha.equals(captcha)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码输入有误");
        }
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", emailAccount);
        User user = this.getOne(queryWrapper);

        // 用户不存在
        if (user == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该邮箱未绑定账号，请先绑定账号");
        }

        if (user.getStatus().equals(UserAccountStatusEnum.BAN.getValue())) {
            throw new BusinessException(ErrorCode.PROHIBITED, "账号已封禁");
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        // 3. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, userVO);
        return userVO;
    }

    @Override
    public UserVO userBindEmail(UserBindEmailRequest userEmailLoginRequest, HttpServletRequest request) {
        String emailAccount = userEmailLoginRequest.getEmailAccount();
        String captcha = userEmailLoginRequest.getCaptcha();
        if (StringUtils.isAnyBlank(emailAccount, captcha)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        if (!Pattern.matches(emailPattern, emailAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不合法的邮箱地址！");
        }
        String cacheCaptcha = redisTemplate.opsForValue().get(CAPTCHA_CACHE_KEY + emailAccount);
        //todo
//        cacheCaptcha="486714";
        if (StringUtils.isBlank(cacheCaptcha)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码已过期,请重新获取");
        }
        captcha = captcha.trim();
        if (!cacheCaptcha.equals(captcha)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码输入有误");
        }
        // 查询用户是否绑定该邮箱
        UserVO loginUser=new UserVO();
        BeanUtils.copyProperties(this.getLoginUser(request),loginUser);
        if (loginUser.getEmail() != null && emailAccount.equals(loginUser.getEmail())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该账号已绑定此邮箱,请更换新的邮箱！");
        }
        // 查询邮箱是否已经绑定
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", emailAccount);
        User user = this.getOne(queryWrapper);
        if (user != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "此邮箱已被绑定,请更换新的邮箱！");
        }
        user = new User();
        user.setId(loginUser.getId());
        user.setEmail(emailAccount);
        boolean bindEmailResult = this.updateById(user);
        if (!bindEmailResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "邮箱绑定失败,请稍后再试！");
        }
        loginUser.setEmail(emailAccount);
        return loginUser;
    }

    @Override
    public UserVO userUnBindEmail(UserUnBindEmailRequest userUnBindEmailRequest, HttpServletRequest request) {
        String emailAccount = userUnBindEmailRequest.getEmailAccount();
        String captcha = userUnBindEmailRequest.getCaptcha();
        if (StringUtils.isAnyBlank(emailAccount, captcha)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        if (!Pattern.matches(emailPattern, emailAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不合法的邮箱地址！");
        }
        String cacheCaptcha = redisTemplate.opsForValue().get(CAPTCHA_CACHE_KEY + emailAccount);
        //todo
//        cacheCaptcha="486714";
        if (StringUtils.isBlank(cacheCaptcha)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码已过期,请重新获取");
        }
        captcha = captcha.trim();
        if (!cacheCaptcha.equals(captcha)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码输入有误");
        }
        // 查询用户是否绑定该邮箱
        UserVO loginUser=new UserVO();
        BeanUtils.copyProperties(this.getLoginUser(request),loginUser);
        if (loginUser.getEmail() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该账号未绑定邮箱");
        }
        if (!emailAccount.equals(loginUser.getEmail())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该账号未绑定此邮箱");
        }
        User user = new User();
        user.setId(loginUser.getId());
        user.setEmail("");
        boolean bindEmailResult = this.updateById(user);
        if (!bindEmailResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "邮箱解绑失败,请稍后再试！");
        }
        loginUser.setEmail(null);
        return loginUser;
    }

    @Override
    public boolean addWalletPoints(Long id, Integer addPoints) {
        LambdaUpdateWrapper<User> userLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        userLambdaUpdateWrapper.eq(User::getId, id);
        userLambdaUpdateWrapper.setSql("points = points + " + addPoints);
        return this.update(userLambdaUpdateWrapper);
    }

    @Override
    public void invokeCount(long userId) {
        this.update(new LambdaUpdateWrapper<User>().setSql("points = points - 1").eq(User::getId,userId));
    }

    @Override
    public boolean userBan(long userId,HttpServletRequest request) {
        User attribute = (User)request.getSession().getAttribute(USER_LOGIN_STATE);
        if(userId==attribute.getId()){
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "不能禁用自己！");
        }
        return this.update(new LambdaUpdateWrapper<User>().eq(User::getId,userId).setSql("status=1"));
    }

    @Override
    public boolean userPick(long userId) {
        return this.update(new LambdaUpdateWrapper<User>().eq(User::getId,userId).setSql("status=0"));
    }
}
