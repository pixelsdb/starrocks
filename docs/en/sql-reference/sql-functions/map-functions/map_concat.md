---
displayed_sidebar: docs
---

# map_concat

## Description

Returns the union of the input maps. If a key is found in multiple maps, this function keeps only the last value among these maps, called LAST WIN. For example, `SELECT map_concat(map{1:3},map{1:'4'});` returns `{1:"4"}`.

This function is supported from v3.1 onwards.

## Syntax

```Haskell
ANY_MAP map_concat(any_map0, any_map1...)
```

## Parameters

`any_mapN`: the map values you want to union. All maps must share a common type. If data types of the input maps are not the same, the return type is the common supertype of the input maps.

## Return value

Returns a MAP of the common supertype of the input maps.

## Examples

```Plain
mysql> SELECT map_concat(map('a',1, 'b',2), map('c',3));
+------------------------------------------+
| map_concat(map{'a':1,'b':2}, map{'c':3}) |
+------------------------------------------+
| {"c":3,"a":1,"b":2}                      |
+------------------------------------------+

mysql> select map_concat(map{1:3},map{'3.323':3});
+--------------------------------------+
| map_concat(map{1:3}, map{'3.323':3}) |
+--------------------------------------+
| {"3.323":3,"1":3}                    |
+--------------------------------------+


mysql> select map_concat(map{1:3},map{1:'4', 3:'5',null:null}, null);
+--------------------------------------------------------+
| map_concat(map{1:3}, map{1:'4',3:'5',NULL:NULL}, NULL) |
+--------------------------------------------------------+
| {null:null,1:"4",3:"5"}                                |
+--------------------------------------------------------+
```
