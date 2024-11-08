---
displayed_sidebar: docs
---

# days_add

## 功能

向日期时间或日期加上指定的天数。

## 语法

```Haskell
DATETIME days_add(DATETIME|DATE date, INT n);
```

## 参数说明

`date`: DATE 或者 DATETIME 类型的表达式。

`n`: 要加上的天数。

## 返回值说明

返回一个 DATETIME 类型的结果。

如果任一参数为 NULL 或者不合法，则返回 NULL。

如果计算结果超出 [0000-01-01 00:00:00, 9999-12-31 00:00:00] 的范围，则返回 NULL。

## 示例

```Plain Text
select days_add('2023-10-31 23:59:59', 1);
+------------------------------------+
| days_add('2023-10-31 23:59:59', 1) |
+------------------------------------+
| 2023-11-01 23:59:59                |
+------------------------------------+

select days_add('2023-10-31 23:59:59', 1000);
+---------------------------------------+
| days_add('2023-10-31 23:59:59', 1000) |
+---------------------------------------+
| 2026-07-27 23:59:59                   |
+---------------------------------------+

select days_add('2023-10-31', 1);
+---------------------------+
| days_add('2023-10-31', 1) |
+---------------------------+
| 2023-11-01 00:00:00       |
+---------------------------+
```

## 关键字

DAY,day
