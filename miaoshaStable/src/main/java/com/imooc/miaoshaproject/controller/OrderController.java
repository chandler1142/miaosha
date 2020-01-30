package com.imooc.miaoshaproject.controller;

import com.imooc.miaoshaproject.error.BusinessException;
import com.imooc.miaoshaproject.error.EmBusinessError;
import com.imooc.miaoshaproject.mq.MQProducer;
import com.imooc.miaoshaproject.response.CommonReturnType;
import com.imooc.miaoshaproject.service.OrderService;
import com.imooc.miaoshaproject.service.model.OrderModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Created by hzllb on 2018/11/18.
 */
@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class

OrderController extends BaseController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    MQProducer producer;

    //封装下单请求
    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId") Integer itemId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoId", required = false) Integer promoId) throws BusinessException {

        String token = null;
        Map<String, String[]> parameterMap = httpServletRequest.getParameterMap();
        if (parameterMap.get("token") == null || StringUtils.isEmpty(token = parameterMap.get("token")[0])) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
        }
        Boolean isLogin = redisTemplate.opsForValue().get(token) != null;
        if (isLogin == null || !isLogin.booleanValue()) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
        }

        String userId = String.valueOf(redisTemplate.opsForValue().get(token));

        if(producer.transactionAsyncReduceStock(Integer.valueOf(userId), promoId, itemId, amount)) {
            return CommonReturnType.create(null);
        } else {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "未知错误");
        }
    }
}
