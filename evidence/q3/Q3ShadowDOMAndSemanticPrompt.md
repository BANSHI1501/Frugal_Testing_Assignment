# Q3 – Dynamic Micro-Frontend Shadow DOM Piercing & Semantic Prompt Architecture

## Part 1: Nested Shadow DOM Traversal (Open & Captured Closed Roots)

```javascript
/*
 Q3: Nested Shadow DOM traversal

 IMPORTANT:
 Run this instrumentation before the application creates
 its custom elements and closed Shadow Roots.
*/

(() => {
    const closedRoots = new WeakMap();

    const originalAttachShadow = Element.prototype.attachShadow;

    Element.prototype.attachShadow = function (options) {
        const root = originalAttachShadow.call(this, options);

        if (options.mode === "closed") {
            closedRoots.set(this, root);
        }

        return root;
    };

    window.__closedShadowRoots = closedRoots;
})();

/*
 Dynamically search nested open and captured closed roots.
 Does not depend on changing tag names, IDs, or CSS classes.
*/

function findSemanticElement(root, predicate) {
    const walker = document.createTreeWalker(
        root,
        NodeFilter.SHOW_ELEMENT
    );

    let current = walker.currentNode;

    while (current) {
        if (predicate(current)) {
            return current;
        }

        const nestedRoot =
            current.shadowRoot ||
            window.__closedShadowRoots?.get(current);

        if (nestedRoot) {
            const result = findSemanticElement(
                nestedRoot,
                predicate
            );

            if (result) {
                return result;
            }
        }

        current = walker.nextNode();
    }

    return null;
}

// Example: locate a button by its accessible name.
const target = findSemanticElement(
    document,
    element =>
        element.getAttribute("role") === "button" &&
        element.getAttribute("aria-label") === "Submit"
);

console.log("Located element:", target);
```

### How It Works
- `attachShadow()` instrumentation captures closed roots at creation time.
- `WeakMap` stores the closed-root references without modifying component names.
- `TreeWalker` dynamically visits elements.
- Recursive traversal searches nested open and captured closed roots.
- The example identifies an element through semantic ARIA information rather than randomized IDs or class names.

### Technical Limitation
This does not bypass browser security. `element.shadowRoot` returns `null` for an already-created closed Shadow Root; traversal only works when instrumentation runs before the closed roots are created and the application permits this instrumentation. Without that access, closed roots cannot be traversed using ordinary page JavaScript.

---

## Part 2: Advanced Semantic ARIA System Prompt

```text
SYSTEM PROMPT: RESILIENT SEMANTIC ARIA LOCATOR ENGINE

ROLE:
You are an advanced UI automation locator architect.
Your task is to analyze raw HTML component trees and
accessibility information to generate reliable, resilient
element locators for dynamic web applications.

OBJECTIVE:
Identify the intended UI element using semantic accessibility
information instead of unstable implementation details.

STRICT LOCATOR RULES:

1. Use semantic roles such as:
   button, textbox, checkbox, radio, link, dialog,
   menu, menuitem, tab, heading and alert.

2. Use accessible names derived from:
   aria-label, aria-labelledby, associated labels
   and accessibility-tree information.

3. Analyze relationships such as:
   aria-controls, aria-expanded, aria-describedby,
   aria-owns and aria-labelledby.

4. Prefer accessible role and name combinations.

5. Use accessibility subtrees to understand nested
   components and parent-child relationships.

6. Handle dynamic component names and changing DOM
   structures without depending on fixed node names.

STRICTLY FORBIDDEN:

- CSS selectors and CSS attributes.
- Absolute structural XPath expressions.
- Raw CSS class strings.
- Randomized element IDs.
- Positional selectors such as nth-child.
- Fragile DOM-depth assumptions.

LOCATOR STRATEGY:

Tier 1: Exact accessible role and accessible name.

Tier 2: Accessible name combined with relevant
        ARIA relationships.

Tier 3: Parent accessibility context and child
        semantic role.

Tier 4: Inspect the accessibility subtree for a
        unique semantic match.

VALIDATION:

- Check whether the locator identifies one unique element.
- Verify the element's semantic role and accessible name.
- Check relevant enabled, expanded or visible states.
- Re-evaluate the locator after dynamic DOM refreshes.
- Never assume that an earlier element reference remains valid.

AMBIGUITY HANDLING:

If multiple elements match, use their semantic context
to disambiguate.

If no unique semantic match exists, do not invent a locator.
Explain which accessibility information is missing.

OUTPUT FORMAT:

1. Target element description.
2. Semantic role.
3. Accessible name.
4. Relevant ARIA relationships.
5. Recommended locator strategy.
6. Validation conditions.
7. Brief explanation of the locator choice.

Provide a concise decision summary, not hidden
internal chain-of-thought reasoning.

FINAL RULE:
Reliability, accessibility semantics and uniqueness
must take priority over locator convenience.
```
