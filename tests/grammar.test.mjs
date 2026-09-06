import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import textmate from 'vscode-textmate';
import oniguruma from 'vscode-oniguruma';

const wasm = await readFile(new URL('../node_modules/vscode-oniguruma/release/onig.wasm', import.meta.url));
await oniguruma.loadWASM(wasm.buffer.slice(wasm.byteOffset, wasm.byteOffset + wasm.byteLength));
const source = JSON.parse(await readFile(new URL('../bundles/neri/syntaxes/neri.tmLanguage.json', import.meta.url), 'utf8'));
const registry = new textmate.Registry({
  onigLib: Promise.resolve({
    createOnigScanner: patterns => new oniguruma.OnigScanner(patterns),
    createOnigString: value => new oniguruma.OnigString(value),
  }),
  loadGrammar: async scope => scope === 'source.neri' ? source : null,
});
const grammar = await registry.loadGrammar('source.neri');

function tokenize(source) {
  let stack = textmate.INITIAL;
  return source.split('\n').map(line => {
    const result = grammar.tokenizeLine(line, stack);
    stack = result.ruleStack;
    return result.tokens.map(token => ({
      text: line.slice(token.startIndex, token.endIndex),
      scopes: token.scopes,
    }));
  });
}

function expectScope(tokens, text, scope) {
  const token = tokens.find(token => token.text === text);
  assert.ok(token, `Missing token ${JSON.stringify(text)}: ${JSON.stringify(tokens)}`);
  assert.ok(token.scopes.includes(scope), `${text}: expected ${scope}, got ${token.scopes}`);
}

test('trailing do block parameters do not leak into the body', () => {
  const lines = tokenize('application() do |message: String, level|\n  console.println(message)\nend');
  expectScope(lines[0], 'do', 'keyword.control.neri');
  expectScope(lines[0], 'message', 'variable.parameter.function.neri');
  expectScope(lines[0], 'level', 'variable.parameter.function.neri');
  expectScope(lines[1], 'message', 'variable.other.neri');
});

test('typed declarations, callbacks, Unicode names, and native syntax', () => {
  const lines = tokenize(`class Box<T>
  public value: T?
  def static identity(value: Int): Int
    let sal水 = fn(value: Int): Int
      return value + 12.5
    end
  end
end
@cabi("external")
def unsafe read(pointer: Byte*): UInt64
  unsafe
    let count = native.sizeOf<Int32>()
  end
end`);
  expectScope(lines[0], 'Box', 'entity.name.type.class.neri');
  expectScope(lines[1], 'public', 'storage.modifier.neri');
  expectScope(lines[2], 'identity', 'entity.name.function.neri');
  expectScope(lines[2], 'Int', 'storage.type.neri');
  expectScope(lines[3], 'sal水', 'variable.other.neri');
  expectScope(lines[3], 'fn', 'keyword.control.neri');
  expectScope(lines[4], '12.5', 'constant.numeric.neri');
  expectScope(lines[8], 'cabi', 'entity.name.function.annotation.neri');
  expectScope(lines[9], 'read', 'entity.name.function.neri');
  expectScope(lines[9], 'UInt64', 'storage.type.neri');
  expectScope(lines[11], 'native', 'keyword.control.neri');
});

test('comments stay out of strings and incomplete strings recover at the next line', () => {
  const lines = tokenize(String.raw`let text = "# inside \"quoted\"\n" # outside
let broken = "unfinished
let next = true`);
  assert.ok(lines[0].some(token => token.text.includes('# inside') && token.scopes.includes('string.quoted.double.neri')));
  expectScope(lines[0], String.raw`\"`, 'constant.character.escape.neri');
  assert.ok(lines[0].some(token => token.text.includes('outside') && token.scopes.includes('comment.line.number-sign.neri')));
  expectScope(lines[2], 'let', 'keyword.control.neri');
  expectScope(lines[2], 'true', 'constant.language.neri');
  assert.ok(lines[2].every(token => !token.scopes.includes('string.quoted.double.neri')));
});

test('keyword boundaries and editor file conventions', async () => {
  const [tokens] = tokenize('let letter = null; fn fn_value native_value IntValue');
  expectScope(tokens, 'letter', 'variable.other.neri');
  expectScope(tokens, 'fn_value', 'variable.other.neri');
  expectScope(tokens, 'native_value', 'variable.other.neri');
  expectScope(tokens, 'IntValue', 'variable.other.neri');
  expectScope(tokens, 'null', 'constant.language.neri');
  const manifest = JSON.parse(await readFile(new URL('../bundles/neri/package.json', import.meta.url), 'utf8'));
  const configuration = JSON.parse(await readFile(new URL('../bundles/neri/language-configuration.json', import.meta.url), 'utf8'));
  assert.deepEqual(manifest.contributes.languages[0].extensions, ['.hk']);
  assert.equal(configuration.comments.lineComment, '#');
});


test('class references and parameters are identified by syntax, not capitalization', () => {
  const lines = tokenize(`def application(request: sumi.Request, transform: fn(String): Response): App
  let app: sumi.App = new sumi.App()
  let result = new box<Request>()
  let upperCaseVariable = IntValue
  return condition ? left : right
end
let callback = fn(request, next)
  return next(request)
end
let nested = new Box<String>()`);
  expectScope(lines[0], 'request', 'variable.parameter.function.neri');
  expectScope(lines[0], 'Request', 'entity.name.type.class.neri');
  expectScope(lines[0], 'Response', 'entity.name.type.class.neri');
  expectScope(lines[0], 'App', 'entity.name.type.class.neri');
  expectScope(lines[1], 'App', 'entity.name.type.class.neri');
  expectScope(lines[1], 'sumi.App', 'entity.name.type.class.neri');
  expectScope(lines[2], 'box', 'entity.name.type.class.neri');
  expectScope(lines[2], 'Request', 'entity.name.type.class.neri');
  expectScope(lines[3], 'IntValue', 'variable.other.neri');
  expectScope(lines[4], 'right', 'variable.other.neri');
  expectScope(lines[6], 'next', 'variable.parameter.function.neri');
  expectScope(lines[7], 'next', 'support.function.neri');
  expectScope(lines[9], 'String', 'storage.type.neri');
});
