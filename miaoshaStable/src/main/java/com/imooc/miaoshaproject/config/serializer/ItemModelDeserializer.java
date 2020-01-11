package com.imooc.miaoshaproject.config.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.imooc.miaoshaproject.service.model.ItemModel;

import java.io.IOException;

public class ItemModelDeserializer extends JsonDeserializer<ItemModel> {

    @Override
    public ItemModel deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        ItemModel model = jsonParser.readValueAs(ItemModel.class);
        return model;
    }

}
