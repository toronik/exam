import {EditorView} from "codemirror"
import {json} from "@codemirror/lang-json"
import {xml} from "@codemirror/lang-xml"
import {http} from "codemirror-lang-http-bak"
import {MergeView} from "@codemirror/merge"
import { highlightSelectionMatches } from '@codemirror/search';
import { foldGutter, syntaxHighlighting, defaultHighlightStyle} from '@codemirror/language';
import { drawSelection} from '@codemirror/view';

function createMergeView(a, b, parent, type) {
    let e =  [
        EditorView.editable.of(false),
        drawSelection(),
        syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
        highlightSelectionMatches(),
        hl(type)
    ]

    return new MergeView({
        a: {doc: a, extensions: e},
        b: {doc: b, extensions: e},
        parent: parent,
        collapseUnchanged: {},
        gutter: true
    })
}

function createEditorView(d, parent, type) {
    return new EditorView({
        doc: d,
        extensions: [
            EditorView.editable.of(false),
            foldGutter(),
            drawSelection(),
            syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
            highlightSelectionMatches(),
            hl(type)
        ],
        parent: parent
    })
}

function hl(type) {
    if (type === 'http') return http()
    else if (type === 'xml') return xml()
    else return json()
}

export { createMergeView, createEditorView }
