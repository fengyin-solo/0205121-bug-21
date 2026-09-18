package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

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

    /** 非数据库列：当前请求语言下是否有字段回退到了中文 */
    @TableField(exist = false)
    private Boolean langFallback;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
