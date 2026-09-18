package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("route")
public class Route implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String nameEn;
    private String nameJa;
    private String description;
    private String descriptionEn;
    private String descriptionJa;
    private Integer days;
    private String theme;
    private String coverImage;
    private String trafficSuggestion;
    private String hotelSuggestion;
    private BigDecimal budget;
    private Long viewCount;
    private Long favoriteCount;

    /** 非数据库列：当前请求语言下是否有字段回退到了中文 */
    @TableField(exist = false)
    private Boolean langFallback;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
