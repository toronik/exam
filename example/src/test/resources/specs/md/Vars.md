# Markdown setting variables support

## Examples

### [Before each example](- "before")

Given `defaultMap`=[{{map a='1000' b=(at) c='str'}}](- "e:set=#defaultMap")

### ~~Before each example~~

### Maps

#### [Maps-1](-)

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
