# Markdown DB support

## Examples

### [Before each example](- "before")

1. Clean tables: __[orders](- "e:db-clean=#TEXT")__.
2. Set up tables:

| id  | client | driver   | status    | created      | updated      |
|-----|--------|----------|-----------|--------------|--------------|
| 1   | 111    | {{NULL}} | PLACED    | {{at '-2d'}} | {{at}}       |
| 2   | 222    | 11       | COMPLETED | {{at}}       | {{at '+2d'}} |
[[Given orders]]

### ~~Before each example~~

### Basic usage

#### [Basic](-)

__Then__

| id        | client | driver     | status    | created      | updated      |
|-----------|--------|------------|-----------|--------------|--------------|
| !{number} | 111    | {{NULL}}   | PLACED    | {{at '-2d'}} | {{at}}       |
| !{string} | 222    | !{notNull} | COMPLETED | {{at}}       | !{within 2d} |
[[Expected orders]]

__When__

| Empty |
|-------|
[[No orders][Given orders]]

__Then__

| Empty |
|-------|
[[No orders][Expected orders]]

### Advanced usage

#### [Advanced](-)

__Given__

| id  | driver | status    | updated |
|-----|--------|-----------|---------|
| 1   | 11     | COMPLETED | {{now}} |
[[Update order](- "e:db-set=orders operation=update")]

| id  | client | driver   | status    | created | updated |
|-----|--------|----------|-----------|---------|---------|
| 3   | 333    | {{NULL}} | PLACED    | {{now}} | {{now}} |
[[Insert order](- "e:db-set=orders operation=insert")]

__Then__

| id                  | client | driver     | status    | created      | updated                         |
|---------------------|--------|------------|-----------|--------------|---------------------------------|
| !{number}>>order1Id | 111    | 11         | COMPLETED | {{at '-2d'}} | !{within 5s}>>order1UpdatedTime |
| !{string}           | 222    | !{notNull} | COMPLETED | {{at}}       | !{within 2d}                    |
| !{number}           | 333    | {{NULL}}   | PLACED    | !{within 5s} | !{within 5s}                    |
[[Expected orders]]

Check subset of orders:

| client | driver     | status    | updated               |
|--------|------------|-----------|-----------------------|
| 111    | 11         | COMPLETED | {{order1UpdatedTime}} |
[[Expected order 1](- "e:db-check=orders where=id={{order1Id}}")]


[Given orders]: - "e:db-set=orders"
[Expected orders]: - "e:db-check=orders awaitAtMostSec=4"
