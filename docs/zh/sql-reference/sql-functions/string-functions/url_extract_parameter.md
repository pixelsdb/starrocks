---
displayed_sidebar: docs
---

# url_extract_parameter

## 功能

从一个 URL 的 query 部分，获取指定参数（`name`）的取值。参数截取按照 [RFC 1866#section-8.2.1](https://datatracker.ietf.org/doc/html/rfc1866.html#section-8.2.1) 指定的方式。如果指定的参数不存在，返回 NULL。

该函数从 3.2 版本开始支持。

## 语法

```haskell
VARCHAR url_extract_parameter(VARACHR str, VARCHAR name)
```

## 参数说明

- `str`: URL 字符串。
- `name`: 指定的参数名称。

## 返回值说明

Returns a VARCHAR value.

## 示例

```plaintext
mysql> select url_extract_parameter("https://starrocks.io/doc?k1=10&k2=3&k1=100", "k1");
+---------------------------------------------------------------------------+
| url_extract_parameter('https://starrocks.io/doc?k1=10&k2=3&k1=100', 'k1') |
+---------------------------------------------------------------------------+
| 10                                                                        |
+---------------------------------------------------------------------------+

mysql> select url_extract_parameter('https://starrocks.com/doc?k0=10&k1=%21%23%24%26%27%28%29%2A%2B%2C%2F%3A%3B%3D%3F%40%5B%5D%20%22%25%2D%2E%3C%3E%5C%5E%5F%60%7B%7C%7D%7E&k2','k1');
+--------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| url_extract_parameter('https://starrocks.com/doc?k0=10&k1=%21%23%24%26%27%28%29%2A%2B%2C%2F%3A%3B%3D%3F%40%5B%5D%20%22%25%2D%2E%3C%3E%5C%5E%5F%60%7B%7C%7D%7E&k2', 'k1') |
+--------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| !#$&'()*+,/:;=?@[] "%-.<>\^_`{|}~                                                                                                                                        |
+--------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
```
