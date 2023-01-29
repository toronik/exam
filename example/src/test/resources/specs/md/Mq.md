# Markdown MQ support

## Examples

### [Before each example](- "before")

- Purge queue **[myQueue](- "e:mq-purge=#TEXT")**.

### ~~Before each example~~

### Basic usage

[Definition list form](https://www.markdownguide.org/extended-syntax/#definition-lists):

<pre class="code language-markdown">
[Send/check](- "mq-send/check=queueName")
:
```json
 {"a" :  1}
```
: [`myVar1=1` `myVar2=2`](/data/mq/msg.json)
</pre>

#### [Basic dl](-)

__When__

[Given messages]
:
```json
{"a" :  1}
 ```
: [my message]

__Then__

[Expected messages]
:
  ```json
  {"a" :  1}
  ```
: [my message]

#### ~~Basic dl~~

Regular list form:

```markdown

- [Send/check](- "mq-send/check=queueName")
-   ```json
    {"a" :  1}
    ```
- [`myVar1=1` `myVar2=2`](/data/mq/msg.json)
```

#### [Basic ul](-)

__When__

- [Given messages]
-   ```json
    {"a" :  1}
    ```
- [my message]

__Then__

- [Expected messages]
-   ```json
    {"a" :  1}
    ```
- [my message]


### Advanced usage

Setting message attributes and headers:

```markdown
- `messageAttribute=some` _header1=1_ _header2=2_ [`myVar1=1` `myVar2=2`](/data/mq/msg.json)
```

#### [Advanced-1](-)

__When__

- [Given messages]
-   ```json
    { "a": 1 }
    ```
- [`myVar1=1` `myVar2=2`](/data/mq/msg.json)
- _d1=1_ _d2=2_ [`myVar1=1` `myVar2=2`][my message]
- `formatAs=xml`
    ```xml
    <message>123</message>
    ```
- `formatAs=xml` _d1={{NULL}}_ _d2=2_
    ```xml
    <message>123</message>
    ```

__Then__

- [Contains exact in any order](- "e:mq-check=myQueue contains=exact_in_any_order")
- [`myVar1=1` `myVar2=2`](/data/mq/msg.json)
- [`myVar1=1` `myVar2=2`][my message] _d1=1_ _d2=2_
-   ```json
    {"a" :  1}
    ```
- `verifyAs=xml` 
    ```xml
    <message>123</message>
    ```
- _d1={{NULL}}_ _d2=2_ `verifyAs=xml`
    ```xml
    <message>123</message>
    ```


[my message]: /data/mq/msg.json
[Given messages]: - "e:mq-send=myQueue"
[Expected messages]: - "e:mq-check=myQueue awaitAtMostSec=4"
