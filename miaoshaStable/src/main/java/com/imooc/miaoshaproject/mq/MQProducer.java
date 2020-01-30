package com.imooc.miaoshaproject.mq;

import com.alibaba.fastjson.JSON;
import com.imooc.miaoshaproject.error.BusinessException;
import com.imooc.miaoshaproject.service.OrderService;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Component
public class MQProducer {

    private DefaultMQProducer producer;

    private TransactionMQProducer transactionMQProducer;

    @Value("${mq.nameserver.addr}")
    private String nameAddr;

    @Value("${mq.topicname}")
    private String topicName;

    @Autowired
    private OrderService orderService;

    @PostConstruct
    public void init() throws MQClientException {
        //做mq producer初始化
        producer = new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(nameAddr);
        producer.setSendMsgTimeout(10000);
        producer.start();

        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.setSendMsgTimeout(10000);
        transactionMQProducer.start();

        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object args) {
                //真正要做的事情, 创建订单
                try {
                    Integer itemId = (Integer) ((Map) args).get("itemId");
                    Integer userId = (Integer) ((Map) args).get("userId");
                    Integer promoId = (Integer) ((Map) args).get("promoId");
                    Integer amount = (Integer) ((Map) args).get("amount");
                    orderService.createOrder(userId, itemId, promoId, amount);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
                return null;
            }
        });
    }

    //事务型同步库存扣减消息
    public boolean transactionAsyncReduceStock(Integer userId, Integer promoId, Integer itemId, Integer amount) {
        Map<String, Object> bodyMap = new HashMap();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);

        Map<String, Object> argsMap = new HashMap();
        argsMap.put("itemId", itemId);
        argsMap.put("amount", amount);
        argsMap.put("userId", userId);
        argsMap.put("promoId", promoId);

        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));

        TransactionSendResult sendResult = null;
        try {
            sendResult = transactionMQProducer.sendMessageInTransaction(message, argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
        }
        if (sendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
            return false;
        } else if(sendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
            return true;
        } else {
            return false;
        }
    }

    //同步库存扣减消息
    public Boolean asyncReduceStock(Integer itemId, Integer amount) {
        Map<String, Object> bodyMap = new HashMap();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);
        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        try {
            SendResult sendResult = producer.send(message, 10000);
            if (sendResult.getSendStatus() == SendStatus.SEND_OK) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

}
