function enableCM() {
    enableCodeMirror('pre code[data-lang="json"]', 'json');
    enableCodeMirror('pre code[data-lang="xml"]', 'xml');
    enableCodeMirror('.json:not(.failure)', 'json');
    enableCodeMirrorMerge('.json.failure', 'json');
    enableCodeMirror('.xml:not(.failure)', 'xml');
    enableCodeMirrorMerge('.xml.failure', 'xml');
    enableCodeMirror('.http:not(.failure)', 'http');
    enableCodeMirrorMerge('.http.failure', 'http');
}

function unescape(input) {
    const e = document.createElement('div');
    e.innerHTML = input;
    return e.childNodes.length === 0 ? "" : e.childNodes[0].nodeValue;
}

function enableCodeMirror(selector, mode) {
  let value, listings = document.querySelectorAll(selector);
  for (let i = 0; i < listings.length; i++) {
    const el = listings[i];
    value = unescape(el.innerHTML);
    if (el.childNodes.length === 1) el.innerHTML = "";
    cm6.createEditorView(value, el, mode);
  }
}

function enableCodeMirrorMerge(selector, mode) {
    let listings = document.querySelectorAll(selector);
    for (let i = 0; i < listings.length; i++) {
        let target = listings[i];
        let expectedValue, actualValue,
            expected = target.querySelector('.expected'),
            actual = target.querySelector('.actual');

        if (expected && actual) {
          expectedValue = unescape(expected.innerHTML);
          actualValue = unescape(actual.innerHTML);

          actual.parentNode.removeChild(actual);
          expected.parentNode.removeChild(expected);
          target.innerHTML = '';

          cm6.createMergeView(expectedValue, actualValue, target, mode);
        }
    }
}
