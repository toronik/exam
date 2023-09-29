# Markdown setting variables support

## Examples

### [Before each example](- "before")

Given `defaultMap`=[{{map a='1000' b=(at) c='str'}}](- "e:set=#defaultMap")

### ~~Before each example~~

### Maps

#### [Maps-1](-)

__Given__

The HTML specification
sdsd ff ddd d
is maintained by the W3C.


Apple
: Pomaceous fruit of plants of the genus Malus in
the family Rosaceae.

Orange
: The fruit of an evergreen tree of the genus Citrus.
ddd1
dd2
: ddddsss
: dd

Await:
: `hasStatus=200` `awSec=8`

Request:
```http request
GET /url
Content-Type: application/json
```

Expected response:
```json
{"ddd": 1}
```

Check request:

```http request
GET /url
Content-Type: application/json
```

```json
{"ddd": 1}
```

Send message to `t` with headers: `a=1`, `b=2`

___e:mq-set=t___

topic
: h
: h
:

Topic:
`h1=1` `h2=2`
```json
 { "id": 54, }
```
`h1=1`
_h2=2_
```json lines
{ "id": 55 } { "id": 55 }
```

Expected messages in `t`:

- [jj][jj]
-   expected headers `a=1`
    ```json
    { "id": 54 }
    ```
-   expected headers `a=2`
    ```json
    { "id": 54 }
    ```


dd
: ddd

```json
{"d": 1}
```

ddd

```http request {.http #example-1}
GET /url
Content-Type: application/json
```

```http_request:{.http_#example-1}
GET /url
Content-Type: application/json
```

~~~~~~~~~~~~~~~~~~~~~~~~~~~~ {.html #example-1}
<p>paragraph <b>emphasis</b>
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

~~~~~~~~~~~~~~~~~~~~~~~~~~~~html {.html #example-1}
<p>paragraph <b>emphasis</b>
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

~~~~~~~~~~~~~~~~~~~~~~~~~~~~ .html
<p>paragraph <b>emphasis</b>
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

~~~~~~~~~~~~~~~~~~~~~~~~~~~~html .html
<p>paragraph <b>emphasis</b>
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

__When__

- `changedDefault1` = [{{map defaultMap a=2}}](- "e:set=#changedDefault1")
- `changedDefault2` = [{{map defaultMap b=(at '1d') c='upd'}}](- "e:set=#changedDefault2")

__Then__

[defaultMap = ](- "c:echo=#defaultMap"):

- `a` = [1000](- "?=#defaultMap.a")
- `b` = [{{at}}](- "e:equals=#defaultMap.b")
- `c` = [str](- "?=#defaultMap.c")

`changedDefault1`=[{a=2, b={{at}}, c=str}](- "e:equals=#changedDefault1")
`changedDefault2`=[{a=1000, b={{at '1d'}}, c=upd}](- "e:equals=#changedDefault2")

#### ~~Maps-1~~

Second example:

#### [Maps-2](-)

__When__

`changedDefault` = [{{map defaultMap a=2}}](- "e:set=#changedDefault")

__Then__

`defaultMap`=[{a=1000, b={{at}}, c=str}](- "e:equals=#defaultMap")
`changedDefault`=[{a=2, b={{at}}, c=str}](- "e:equals=#changedDefault")

*[HTML specification]: Hyper Text Markup Language
*[W3C]:  World Wide Web Consortium
*[is maintained by]:  ddddd
*[Send message to]:  e:mq-set-topic
*[with headers]:  e:mq-set-headers


[Given messages]: - "e:mq-send=myQueue"
