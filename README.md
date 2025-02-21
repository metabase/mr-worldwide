# Mr. Worldwide

## The Two Locales

Mr. Worldwide supports localizing into two distinct locales: the system locale and the (current) user's locale. The
system locale can be used for strings that don't have a specific user associated with them such as log messages (if you
are a kook and want to translate your log messages); the user locale can be used for ones that do (such as translating
your UI or error messages into the current user's locale). If no user locale is specified, Mr. Worldwide will fall back
to the site locale as the default.

To translate a string for the system, use `mr-worldwide.core/trs` and for the user's locale, use the similar
`mr-worldwide.core/tru`. These are short for *TRanslate System* and *TRanslate User*, respectively.

### How it works

At a high level, the string to be translated is treated as a lookup key into a map of source-string -> localized-string.
This translated string is used like so:

```clojure
;; from the source of `mr-worldwide.core/translate`
(.format (MessageFormat. looked-up-string) (to-array args))
```

Everything else is largely bookkeeping. This uses the
[java.text.MessageFormat](https://docs.oracle.com/javase/7/docs/api/java/text/MessageFormat.html) class for splicing in
format args.

The functions `trs` and `tru` create instances of two records, `SiteLocalizedString` and `UserLocalizedString`
respectively with overrides to the `toString` method. This method will do the lookup to the current locale (user or site
as appropriate), lookup the string to be translated to the associated translated string, and then call the `.format`
method on the `MessageFormat`.

### Format Args

Besides string literals, we also want to translate strings that have arguments spliced into the middle. We use the
syntax from the [java.text.MessageFormat](https://docs.oracle.com/javase/7/docs/api/java/text/MessageFormat.html) class
mentioned before. These are zero-indexed args of the form `{0}`, `{1}`.

eg,

```clojure
(trs "{0} accepted their {1} invite" (:common_name new-user) (app-name-trs))
(tru "{0}th percentile of {1}" p (aggregation-arg-display-name inner-query arg))
(tru "{0} driver does not support foreign keys." driver/*driver*)
```

#### Escaping

Every string language needs an escape character. Since `{0}` is an argument to be spliced in, how would you put a
literal `{0}` in the string? The apostrophe serves this role and is described in the MessageFormat
[javadocs](https://docs.oracle.com/javase/7/docs/api/java/text/MessageFormat.html).

These is an unfortunate side effect of this though. Since the apostrophe is such a commeon part of speech (especially in
French), we often can end up with escape characters used as a regular part of a string rather than the escape character.
Format strings need to use two single quotes e.g. `(deferred-tru "SAML attribute for the user''s email address")` where
you'd normally use one to escape it.

In our experience we've ended up with lots of incorrectly translated strings that use a single apostrophe incorrectly.
(e.g. "l'URL" instead of "l''URL"). `mr-worldwide.build.artifacts` will try to identify these and fix them
automatically.
