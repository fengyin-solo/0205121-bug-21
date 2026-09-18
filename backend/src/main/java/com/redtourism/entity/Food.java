package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Data
@TableName("food")
public class Food implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String nameEn;
    private String nameJa;
    private String description;
    private String descriptionEn;
    private String descriptionJa;
    private String category;
    private BigDecimal price;
    private String coverImage;
    private Long storeId;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 多语言回退标记：key 为发生中文回退的字段名，仅接口响应时填充，非数据库列 */
    @TableField(exist = false)
    private Map<String, Boolean> fallbackFields;
}
