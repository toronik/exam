### Bundle Codemirror 6

The official docs mention how to create a bundle [here](https://codemirror.net/examples/bundle/):

1. Install npm packages `rollup` and `@rollup/plugin-node-resolve`
2. Install CodeMirror 6 npm packages that you want (for example: `@codemirror/lang-python` if you want python support)

You can simply run `npm install` in the root of this repo which will install all the dependencies currently used by `cm6.bundle.js` as well as `rollup` and `minify`.

Using [editor.js](src/main/resources/ext/codemirror/editor.js) as a template: delete or add extensions.

Create bundle with:

```shell
cd src/main/resources/ext/codemirror/ && npx rollup editor.js -f iife -o cm6.bundle.js -p @rollup/plugin-node-resolve --output.name cm6
```

(Optional) Minify bundle with:

```shell
cd src/main/resources/ext/codemirror/ && npx minify cm6.bundle.js > cm6.bundle.min.js
```
