# 04 — HTML & CSS: The Minimum

> **≈ 3 hours total. Deliberately minimal** (see [ROADMAP §2.4](../ROADMAP.md#24-weighting-fixed)). Weeks 6–7.
> Target: build FlowGrid's login form, inventory table, pick/pack board columns and warehouse tiles — readable,
> accessible, no framework. You are not becoming a CSS specialist.

---

## 1. Document skeleton (15 min)

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>FlowGrid</title>
    <link rel="stylesheet" href="/styles.css" />
  </head>
  <body>
    <header>…</header>
    <nav>…</nav>
    <main>…</main>
    <footer>…</footer>
    <script type="module" src="/main.js"></script>
  </body>
</html>
```

Vite's `index.html` looks exactly like this with `<div id="root"></div>` inside `<body>`.

**Semantic elements** give structure, accessibility and free keyboard behaviour: `header`, `nav`, `main`,
`section`, `article`, `footer`, `h1–h6` (in order), `button` (for actions), `a` (for navigation), `ul/li`, `table`.

> Use `<button>` for clicks, not `<div onClick>`. A div is not focusable, not keyboard-activatable, and screen readers don't announce it.

---

## 2. Forms (45 min)

```html
<form id="login" novalidate>
  <div class="field">
    <label for="email">Email</label>
    <input id="email" name="email" type="email" autocomplete="username" required />
  </div>

  <div class="field">
    <label for="password">Password</label>
    <input id="password" name="password" type="password" autocomplete="current-password"
           required minlength="8" aria-describedby="pw-error" />
    <p id="pw-error" class="error" role="alert" hidden>Password must be at least 8 characters.</p>
  </div>

  <div class="field">
    <label for="warehouse">Warehouse</label>
    <select id="warehouse" name="warehouse">
      <option value="1">Berlin (BER-1)</option>
      <option value="2">Hamburg (HAM-1)</option>
    </select>
  </div>

  <label><input type="checkbox" name="remember" /> Remember me</label>

  <button type="submit">Sign in</button>
</form>
```

Key facts:
- Every input needs a `<label for>` matching its `id` (accessibility **and** click target; RTL's `getByLabelText` depends on it).
- `name` is the key in `FormData`.
- A `<button>` inside a form defaults to `type="submit"`. Use `type="button"` for non-submit buttons (classic bug: "Cancel" submits the form).
- Built-in validation attributes: `required`, `minlength`, `maxlength`, `min`, `max`, `pattern`, `type="email"`.
  `novalidate` turns off browser popups so you can show your own messages. **Client validation is UX; the server (Bean Validation) is the real gate.**

```js
document.getElementById("login").addEventListener("submit", async (e) => {
  e.preventDefault();                                  // stop full-page reload
  const data = Object.fromEntries(new FormData(e.currentTarget));
  console.log(data); // { email: "...", password: "...", warehouse: "1", remember: "on" }
});
```

---

## 3. Tables (20 min)

```html
<table class="inventory">
  <caption>Inventory — Berlin (BER-1)</caption>
  <thead>
    <tr><th scope="col">SKU</th><th scope="col">Name</th><th scope="col">On hand</th><th scope="col">Available</th></tr>
  </thead>
  <tbody>
    <tr><td>BOLT-M8-50</td><td>Bolt M8×50</td><td class="num">120</td><td class="num"><span class="badge low">4</span></td></tr>
  </tbody>
</table>
```

Use tables for **tabular data** (inventory levels, orders, build history). Never for layout.

---

## 4. CSS essentials (40 min)

```css
/* Sensible reset */
*, *::before, *::after { box-sizing: border-box; }   /* width includes padding + border */
body { margin: 0; font-family: system-ui, sans-serif; line-height: 1.5; color: #1f2328; }

/* Selectors, by specificity (low → high): element < .class < #id < inline style */
.badge { padding: 2px 8px; border-radius: 999px; font-size: 0.75rem; }
.badge.low { background: #fde2e1; }
.num { text-align: right; font-variant-numeric: tabular-nums; }   /* numbers line up */
.inventory th, .inventory td { padding: 8px 12px; border-bottom: 1px solid #ddd; text-align: left; }
button:hover { cursor: pointer; }
input:focus-visible { outline: 2px solid #0969da; }

/* Custom properties (variables) */
:root { --gap: 16px; --danger: #cf222e; }
.error { color: var(--danger); }
```

**Box model:** content → padding → border → margin. With `box-sizing: border-box`, `width` includes padding and border.

**Units:** `px` for borders, `rem` for font sizes/spacing (relative to root font), `%`/`fr` for layout.

---

## 5. Flexbox — the one layout tool you need (45 min)

Flexbox lays children out along one axis.

```css
.row      { display: flex; gap: var(--gap); align-items: center; }       /* horizontal */
.column   { display: flex; flex-direction: column; gap: 8px; }
.spacer   { margin-left: auto; }                                         /* push to the right */
.toolbar  { display: flex; justify-content: space-between; align-items: center; }
.wrap     { display: flex; flex-wrap: wrap; gap: 12px; }
.grow     { flex: 1; }                                                   /* take remaining space */
```

| Property (on container) | Controls |
|---|---|
| `flex-direction` | main axis: `row` (default) / `column` |
| `justify-content` | alignment along main axis: `flex-start`, `center`, `space-between` |
| `align-items` | alignment on cross axis: `stretch` (default), `center`, `flex-start` |
| `gap` | spacing between items |
| `flex-wrap` | allow wrapping |

| Property (on item) | Controls |
|---|---|
| `flex: 1` | grow to fill |
| `flex: 0 0 280px` | fixed-width column |
| `min-width: 0` | allow text truncation inside a flex item |

### FlowGrid pick/pack board layout

```html
<div class="board">
  <section class="lane"><h2>ALLOCATED</h2><article class="card">…</article></section>
  <section class="lane"><h2>PICKING</h2></section>
  <section class="lane"><h2>PACKED</h2></section>
  <section class="lane"><h2>SHIPPED</h2></section>
</div>
```

```css
.board { display: flex; gap: 16px; overflow-x: auto; padding: 16px; }
.lane  { flex: 0 0 280px; display: flex; flex-direction: column; gap: 8px;
         background: #f6f8fa; border-radius: 8px; padding: 12px; }
.card  { background: white; border: 1px solid #d0d7de; border-radius: 6px; padding: 8px; }
```

### Warehouse tiles (CSS Grid, one rule is enough) — also ForgeCI's build grid later

```css
.tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
```

### Responsive in one media query

```css
@media (max-width: 640px) {
  .board { flex-direction: column; }
  .lane  { flex-basis: auto; }
}
```

---

## 6. DOM basics (15 min, for understanding what React does for you)

```js
const list = document.querySelector("#order-list");
const li = document.createElement("li");
li.textContent = order.number;          // textContent, NOT innerHTML — avoids XSS
list.append(li);
list.addEventListener("click", (e) => {
  const item = e.target.closest("li"); // event delegation
});
```

React replaces manual DOM updates with declarative rendering. It also escapes text by default —
`dangerouslySetInnerHTML` is the name React gives the XSS footgun.

---

## Break it

1. Remove `box-sizing: border-box` and give an input `width: 100%; padding: 12px`. It overflows its container. Why?
2. Put a `<button>Cancel</button>` in the form without `type`. Click it. The form submits.
3. Remove `e.preventDefault()`. The page reloads and your console log vanishes.
4. Remove `<label for>`. Click the label text — focus no longer moves to the input.
5. Put a long unbroken title in a flex card. It overflows. Fix with `min-width: 0` + `overflow-wrap: anywhere`.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `<div>` as buttons | `<button type="button">` |
| Inputs without labels | `<label for>` or wrap input in `<label>` |
| Layout with tables or floats | flexbox / grid |
| Fighting specificity with `!important` | lower-specificity classes, fewer IDs in CSS |
| `innerHTML` with user data | `textContent` (or React) |

---

## Interview Q&A

<details><summary>What is the CSS box model?</summary>

Every element is a box: content, padding, border, margin. `box-sizing: content-box` (default) makes `width`
apply to content only; `border-box` includes padding and border, which is easier to reason about.
</details>

<details><summary>Flexbox vs Grid?</summary>

Flexbox is one-dimensional (a row or a column) — toolbars, form rows, board lanes. Grid is two-dimensional —
card grids and page layouts. I use flex by default and grid for a responsive tile grid.
</details>

<details><summary>Why does semantic HTML matter?</summary>

Accessibility (screen readers, keyboard navigation), SEO, and built-in behaviour: a `<button>` is focusable
and activates on Enter/Space; a `<form>` submits on Enter. It also makes accessible-query testing (Testing Library) possible.
</details>

---

## Mastery checklist

- [ ] Build the FlowGrid login form with labels, validation attributes and a custom error message.
- [ ] Build an inventory table with `thead`/`tbody`/`scope`.
- [ ] Build the four-lane pick/pack board with flexbox from a blank CSS file.
- [ ] Build the warehouse tile grid that reflows on narrow screens.
- [ ] Explain box model, `box-sizing` and specificity in one minute.
