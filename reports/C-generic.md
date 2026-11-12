# C-generic report (task T5)

Items done in this task: 10, 25, 26, in that order, one commit each. Item 9 is task T13 and was
not touched. No constructor of `FormAnalyzer`, `FormFiller` or `FieldHandler` was changed, and
`AIResponseParser` was not edited. `J/` = `src/main/java/com/cvmaker/`.

## Item 10: FieldHandler cannot fill select/textarea, and silently picks the first option

**What was wrong**
- `FormField.getType()` returned "text" for `<select>` and `<textarea>` (they have no `type`
  attribute), so `FieldHandler` called `fill()` on selects, which Playwright rejects.
- When no option matched, the select and radio code picked the first option, which can answer an
  eligibility or legal question wrongly without anyone noticing.
- `verify` used `inputValue()`, which is always "on" for a checkbox, so a failed tick passed.

**What changed**
- `J/crawler/FormField.java`: stores the lower-case `tagName` (read with
  `evaluate("el => el.tagName")`), adds `getTagName()`/`setTagName()`, `getKind()` and the pure
  `static kindOf(tagName, type)`: the tag decides for select/textarea, otherwise the type, default
  "text". `getType()` is unchanged.
- `J/crawler/generic/FieldHandler.java`: dispatches on `getKind()`. Select options are read first
  and matched by value, then by visible text (case and whitespace insensitive); only a match is
  selected, and `selectOption` is never called with a guess (it would also wait for its timeout).
  Radios match by value. With no match the field is left unfilled and recorded; the new
  `getUnfilledFields()` lists "identifier: reason". Checkboxes are verified with `isChecked()`
  against the wanted state, radios with `isChecked()` on the chosen button, selects by comparing
  `inputValue()` with the chosen value. Unsupported kinds (file, date, ...) are recorded as unfilled.
  Pure helpers `findOptionIndex`, `findOptionValue`, `isAffirmative` and the `Option` record are
  package-private for tests.
- `J/crawler/generic/FormFiller.java`: after filling, prints the unfilled fields so the user
  completes them before submitting. Constructor unchanged.

**How verified**
- `src/test/java/com/cvmaker/crawler/generic/FieldHandlerTest.java` (Mockito mocks of `Locator` and
  `Page`): select dispatched on tag and `selectOption("Y")` called, no `fill`; no matching option
  leaves it unfilled with no `selectOption`; textarea filled; checkbox reported as failed when
  `isChecked()` is false although `inputValue()` is "on"; uncheck verified; radio with no match
  checks nothing; radio with a match checks only that one; `kindOf` and the option matcher tables.
  With the old code the select test fails (`fill` is called instead of `selectOption`) and the
  checkbox test fails (`inputValue` "on" counted as success).
- `mvn -q test` passes.

**Left / not done**
- Radios match on the value attribute only; matching on the choice's label text is added in item 25.
- Kinds such as `date`, `search` and `file` are reported as unfilled rather than handled.
