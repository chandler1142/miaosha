package com.imooc.miaoshaproject.controller;

import com.imooc.miaoshaproject.error.BusinessException;
import com.imooc.miaoshaproject.error.EmBusinessError;
import com.imooc.miaoshaproject.mq.MQProducer;
import com.imooc.miaoshaproject.response.CommonReturnType;
import com.imooc.miaoshaproject.service.ItemService;
import com.imooc.miaoshaproject.service.OrderService;
import com.imooc.miaoshaproject.service.PromoService;
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

    @Autowired
    ItemService itemService;

    @Autowired
    PromoService promoService;

    //生成秒杀令牌
    @RequestMapping(value = "/generateToken", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generateToken(@RequestParam(name = "itemId") Integer itemId,
                                          @RequestParam(name = "promoId") Integer promoId) throws BusinessException {

        //根据用户登录token获取用户信息
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

        //获取秒杀访问令牌
        String promoToken = promoService.generateSecondKillToken(promoId, itemId, Integer.valueOf(userId));
        if (promoToken == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "生成令牌失败");
        }

        return CommonReturnType.create(promoToken);
    }

    //封装下单请求
    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId") Integer itemId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoId", required = false) Integer promoId,
                                        @RequestParam(name = "promoToken", required = false) String promoToken) throws BusinessException {

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


        //校验活动令牌
        if(promoId != null) {
            String inredisPromoToken = (String) redisTemplate.opsForValue().get("promo_token_" + promoId + "_userid_" + userId + "_itemid_" + itemId);
            if(inredisPromoToken == null) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀令牌校验失败");
            }
            if(!StringUtils.equals(promoToken, inredisPromoToken)) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀令牌校验失败");
            }
        }


        //判断库存是否已经售罄
        if (redisTemplate.hasKey("promo_item_stock_invalid_" + itemId)) {
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH, "库存不足");
        }

        //先加入库存流水init状态
        String stockLogId = itemService.initStockLog(itemId, amount);

        //再去完成对应的下单事务型消息机制
        if (producer.transactionAsyncReduceStock(Integer.valueOf(userId), promoId, itemId, amount, stockLogId)) {
            return CommonReturnType.create(null);
        } else {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "下单失败");
        }
    }
}
