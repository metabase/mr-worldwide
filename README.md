[![License](https://img.shields.io/badge/license-Eclipse%20Public%20License-blue.svg?style=for-the-badge)](https://raw.githubusercontent.com/camsaul/toucan2/master/LICENSE)
[![GitHub last commit](https://img.shields.io/github/last-commit/camsaul/toucan2?style=for-the-badge)](https://github.com/camsaul/toucan2/commits/)

[![Clojars Project](https://clojars.org/io.github.metabase/mr-worldwide/latest-version.svg)](https://clojars.org/io.github.metabase/mr-worldwide)
[![Clojars Project](https://clojars.org/io.github.metabase/mr-worldwide.build/latest-version.svg)](https://clojars.org/io.github.metabase/mr-worldwide.build)

# Mr. Worldwide

Mr. Worldwide is set of Clojure(Script) libraries for internationalization, spun out from the i18n tooling inside
[Metabase](https://github.com/metabase/metabase) we've been iterating on for the past 10 years or so.

It is broken out into two libraries:

* `io.github.metabase/mr-worldwide` -- code for marking strings for i18n and for translating them at runtime. Typically
  this will be included in your project dependencies (i.e., in the uberjar, if you were to build one)

* `io.github.metabase/mr-worldwide.build` -- code for building a `.pot` translation template from your Clojure source
  files, and for building EDN and JSON bundles from translated `.po` files for use in Clojure and
  JavaScript/ClojureScript respectively. Typically these steps will be called as part of your build process, so this
  library is only needed as a build dependency.

# Translating Strings in your Application with `mr-worldwide`

You can mark strings for translation with the `tru` and `trs` family of macros in `mr-worldwide.core`. `trs` stands
*TRanslate System*, while `tru` stands for *TRanslate User*, and translate to the system locale and user locale
respectively.

The system locale should be used for strings that don't have one specific user associated with them, for example a bot
that posts notifications in a Slack channel or your app log messages (if you are a kook and want to translate them).

The user locale should be used for strings that have on specific user associated with them -- for example you can use it
to translate your UI or user-facing error messages into their locale.

If a specific user locale isn't specified, the site locale serves as a fallback/default user locale. For example you
might want to have your site default to Spanish but let users override this with a different locale if *sólo hablan un
poco de Español*.

Basic usage looks something like this:

```clj
(require '[mr-worldwide.core :as i18n])

(defn startup-message []
  (i18n/trs "The system is now starting..."))
```

Under the hood, `trs` macroexpands to something like

```clj
(str (SystemLocalizedString. "The system is now starting..."))
```

`SystemLocalizedString` and `UserLocalizedString` are two custom record types that hold on to the original string and
themselves appropriately when you call their `toString()` method (e.g., when you pass them to `str`). This finds the
appropriate matching format string from the resources built by `mr-worldwide.build` and then uses
[`java.util.MessageFormat`](https://docs.oracle.com/javase/8/docs/api/java/text/MessageFormat.html) (in the JVM) or
[`ttag`](https://ttag.js.org/) (in ClojureScript) to handle argument substitution, e.g.

```clj
(.format (MessageFormat. looked-up-string) (to-array args))
```

## Arguments

`trs`, `trn`, and friends support zero-indexed argument placeholders like `{0}` or `{1}`. These are passed directly to
[`java.util.MessageFormat`](https://docs.oracle.com/javase/8/docs/api/java/text/MessageFormat.html), so refer to its
JavaDoc for more details on the syntax.

Examples:

```clojure
(trs "{0} accepted their {1} invite" user-name group-name)
(tru "{0}th percentile of {1}" percentile field)
(tru "{0} does not support foreign keys." database-name)
```

## Translating Plurals

You can use `trsn` (*TRanslate System N*) and `trun` (*TRanslate User N*) for translating strings that may or may not
need to be pluralized depending on their arguments.

```clj
(trun "{0} can" "{0} cans" number-of-cans)

;; e.g.
(trun "{0} can" "{0} cans" 1) ; => "1 can"
(trun "{0} can" "{0} cans" 2) ; => "2 cans"
```

You can also use `trsn` and and `trun` even if the format string doesn't have any placeholders, e.g.

```clj
(i18n/trun "Minute" "Minutes" n)

;; e.g.
(i18n/trun "Minute" "Minutes" 1) => "1 Minute"
(i18n/trun "Minute" "Minutes" 2) => "2 Minutes"
```

## Deferred Translation

As noted above, `trs`, `tru`, and the `-n` variations all translate their format string to the appropriate locale when
they are evaluated. If you want to defer translation until later, you can use the `deferred-` variations of these
functions instead:

```clj
(def error-message (deferred-tru "You broke it."))

(defn handle-request [request]
    {:status 500, :body (str error-message)})
```

These basically macroexpand into something like

```clj
(UserLocalizedString. "You broke it.")
```

Which means you can call `str` on it whenever you need them to be translated; they are translated appropriately each
time.

### Automatically Translating Deferred Translations

It can be a good idea to add mappings to JSON encoders or other similar tooling to automatically handle
`mr_worldwide.core.SiteLocalizedString` and `UserLocalizedString`, so you don't need to remember to manually call `(str
...)` on it. For your convenience, Mr. Worldwide adds these for [Cheshire](https://github.com/dakrone/cheshire):

```clj
(defn- localized-to-json [localized-string json-generator]
  (json/generate-string json-generator (str localized-string)))

(cheshire.generate/add-encoder UserLocalizedString localized-to-json)
(cheshire.generate/add-encoder SiteLocalizedString localized-to-json)
```

If you're using a different JSON library, you might want to do something similar.

## Single Quotes

The single quote (`'`) serves as the escape character in
[`java.util.MessageFormat`](https://docs.oracle.com/javase/8/docs/api/java/text/MessageFormat.html), so to get a single
quote or apostrophe in your output you need to escape it with another single quote, i.e. you need to use two single
quotes.

```clj
;;; good
(deferred-tru "SAML attribute for the user''s email address")

;;; WRONG!!!
(deferred-tru "SAML attribute for the user's email address")
```

`trs`, `tru` and friends will attempt to find incorrectly escaped single quotes and error at macroexpansion time, but
this is a best effort and we can't currently catch everything (once `clojure.reader.mind` drops this may change).

Both the original format strings and translated strings need to follow this rule.

Since the apostrophe is such a common part of speech (especially in French), we often can end up with escape characters
used as a regular part of a string rather than the escape character. In our experience we've ended up with lots of
incorrectly translated strings that use a single apostrophe incorrectly. (e.g. `l'URL` instead of `l''URL`).
`mr-worldwide.build.artifacts` will try to identify these and fix them automatically.

## Setting User Locale

You can bind the current user locale with the dynamic variable `mr-worldwide.core/*user-locale*`. A typical place to do
this might be in Ring middleware, e.g.

```clj
(defn current-user-locale [request]
  ...)

(defn middleware [handler]
  ;; you likely only need either the sync 1-arity or async 3-arity instead of both
  (fn
    ([request]
     (binding [mr-worldwide.core/*user-locale* (current-user-locale request)]
       (handler request)))
    ([request respond raise]
     (binding [mr-worldwide.core/*user-locale* (current-user-locale request)]
       (handler request respond raise)))))
```

How you determine user locale for a request is up to you. One option is to look at the [`Accept-Language`
header](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language). Another is to store the user's
preferred language in your application database -- this is the approach Metabase takes.

`*user-locale*` can be bound to a two-letter ISO language code string like `en` (language-only) or `en_US` (language
plus country), a keyword version of these like `:en`, `:en-US`, or `:en/US`, a `java.util.Locale`, or a thunk (a
function that takes no arguments) that when called returns one of the above.

## Setting Site Locale

You can set the site locale with `*site-locale*` or by calling `set-default-site-locale!`. These accept the same
different types of arguments as `*user-locale*` above.

If these are unset, Mr. Worldwide falls back to the JVM default Locale, `(java.util.Locale/getDefault)`. You can specify
this with Java properties `user.language` and `user.country`, e.g.

```
-Duser.language=en -Duser.country=US
```

## Locale Fallback

When translating format strings Mr. Worldwide will look for translation resource bundles that match both the relevant
language and country, and fall back to looking in other bundles of the same language.

For example if the user locale is set to `en_MX` (Mexican Spanish) but we don't have a translation for a specific format
string in `en_MX`, Mr. Worldwide will try looking for one in `en` (Spanish with no country specified); if it fails to
find one there it will try looking in any other `en_*` bundles available (e.g. `en_ES` -- Spanish Spanish).

## Configuration

<a name="configuration"></a>

By default Mr. Worldwide will read available locales by looking on your classpath for `mr-worldwide/config.edn`, and for
EDN resources by looking for files like `mr-worldwide/clj/pt-BR.edn`. `mr-worldwide.build` normally generates these
files in your `resources` directory, so as long as `resources` is on your classpath (or copied into your uberjar) things
will work without further tweaks. If you configure `mr-worldwide.build` to generate the files somewhere else, you will
need to tell Mr. Worldwide where to find these files:

- You can tell it where to find the config file by setting the JVM system property `mr-worldwide.config-filename` or by
  calling `set-config-filename!`

- You can tell it which directory to look for EDN resources in by setting the JVM system property
  `mr-worldwide.clj-bundle-directory` or by calling `set-clj-bundle-directory!`.

## `ttag` Integration (Cljs)

For ClojureScript usage, `trs` and `tru` compile to [`ttag`](https://ttag.js.org/) function calls, and
`mr-worldwide.build` generates JSON resources for `ttag`'s consumption. Besides including the library as an additional
dependency, you'll need a little bit of additional glue to make things work.

The gist is that you need to load the relevant JSON bundle from `resources/mr-worldwide/cljs` and call `ttag`'s
`addLocale()` and `setLocale()` functions.

Here's an example of how to do this adapted from how we use it at Metabase.

First, add some code to load up the JSON bundle for the current locale:

```clj
;; it's a good idea to memoize this
(defn json-resource [locale]
  (let [locale-str (str/replace (str locale) \- \_)]
    (some-> (io/resource (str "mr-worldwide/cljs/" locale-str)) slurp)))
```

Next, include inject this JSON into your `index.html`:

```html
-- example template
<script type="application/json" id="_userLocalization">
    {{json}}
</script>
```

Finally, use `ttag` `addLocale` to load the translations and `useLocale` to use them:

```js
import { addLocale, useLocale } from "ttag";

function setLanguage() {
  const translationsObject = JSON.parse(document.getElementById("_userLocalization").textContent);
  const locale = translationsObject.headers.language;
  const msgs = translationsObject.translations[""];

  // we delete msgid property since it's redundant, but have to add it back in to
  // make ttag happy
  for (const msgid in msgs) {
    if (msgs[msgid].msgid === undefined) {
      msgs[msgid].msgid = msgid;
    }
  }

  // add and set locale with ttag
  addLocale(locale, translationsObject);
  useLocale(locale);
}
```

Refer to these files for a real-world working example:

- [`index.clj`](https://github.com/metabase/metabase/blob/8ea5431774c03bd4450a05e21e766c1ef0c1c244/src/metabase/server/routes/index.clj)
- [`index_bootstrap.js`](https://github.com/metabase/metabase/blob/8ea5431774c03bd4450a05e21e766c1ef0c1c244/resources/frontend_client/inline_js/index_bootstrap.js)
- [`i18n.js`](https://github.com/metabase/metabase/blob/8ea5431774c03bd4450a05e21e766c1ef0c1c244/frontend/src/metabase/lib/i18n.js)

### Note

These steps are currently more complicated than I'd like -- PRs to simplify the process of using Mr. Worldwide with
ClojureScript would be greatly appreciated!

# Building Translation Resources with `mr-worldwide.build`

You can use `io.github.metabase/mr-worldwide.build` to build the translation resources that power
`io.github.metabase/mr-worldwide`. When using Mr. Worldwide, there are three steps to getting your stuff translated:

1. Generate a `.pot` translation template file from your source files
2. Send your `.pot` template to your translators and get translated `.po` files in return
3. Convert your `.po` files to EDN files (for consumption by Mr. Worldwide in the JVM) and JSON files (for consumption
   by `ttag` in ClojureScript)

`mr-worldwide.build` handles step 1 and 3 for you; step 2 is left as an exercise for the reader. At the time of this
writing, Metabase uses [POEditor](https://poeditor.com/) for translation; feel free to copy, adapt, or derive
inspiration from our scripts for [uploading `.pot`
files](https://github.com/metabase/metabase/blob/00ec1bf63308c2f44a3a8e1b510ca1787451c877/bin/i18n/export-pot-to-poeditor)
and [fetching translated `.po`
files](https://github.com/metabase/metabase/blob/00ec1bf63308c2f44a3a8e1b510ca1787451c877/bin/i18n/import-po-from-poeditor).

## Generating a `.pot` Translation Template File

Mr. Worldwide uses [grasp](https://github.com/borkdude/grasp) to walk your Clojure source files and find usages or
`trs`, `tru`, and friends and [`JGetText`](https://mvnrepository.com/artifact/org.fedorahosted.tennera/jgettext) to
generate a `.pot` file.

Call

```clj
(mr-worldwide.build.pot/build-pot! config) ; config should be a map or nil
```

from your `build.clj` script, or with `clojure -X` e.g.

```
clojure -X:build:mr-worldwide.build.pot/build-pot! '{...}'
```

to generate the file. You aren't required to specify anything in `config`; but if you want to override things it default
to:

```clj
{;; where to output the generate `.pot` file
 :pot-filename "target/mr-worldwide/strings.pot"

 ;; directories to look for Clojure source files in to scrape for tru/trs
 :source-paths ["src"]

 ;; optional additional messages to translate
 :overrides nil}
```

`:overrides` if specified should be a sequence of maps with `:file` and `:message` keys, e.g.

```clj
[{:file    "/src/metabase/analyze/fingerprint/fingerprinters.clj"
  :message "Error generating fingerprint for {0}"}]
```

## Generating EDN and JSON Artifacts

Generate artifacts by calling

```clj
(mr-worldwide.build.artifacts/create-artifacts! config)
```

from your `build.clj` or with `clojure -X` e.g.

```
clojure -X:build mr-worldwide.build.artifacts/create-artifacts! {}
```

As above, you should be ok with the `config` defaults, but you can override them if needed; the defaults are:

```clj
{;; directory to look for translated `.po` files in
 :po-files-directory "target/mr-worldwide"

 ;; base directory to output generated i18n resource bundle artifacts to
 :target-directory "resources/mr-worldwide"

 ;; directory to output EDN resources for consumption in the JVM
 :clj-target-directoy "<target-directory>/clj"

 ;; directory to output JSON resources for consumption in ClojureScript
 :cljs-target-directory "<target-directory>/cljs"

 ;; path to write the generated config file to
 :config-filename "<target-directory>/config.edn"}
```

Note that if you change these defaults you'll need to tell `mr-worldwide` where to look for things; see the section
about [Configuration](#configuration) above.

# Test Utils (Mocking)

Mr. Worldwide ships with a few convenient helpers for testings things. Besides being able to bind `*site-locale*` and
`*user-locale*`, you can use `with-mock-i18n-bundles` to mock the resource bundles used by `tru`, `trs`, and friends to
test i18n behavior:

```clj
(require '[mr-worldwide.core :as i18n]
         '[mr-worldwide.test-util :as i18n.tu])

(i18n.tu/with-mock-i18n-bundles {"es" {:messages {"must be {0} characters or less"
                                                  "deben tener {0} caracteres o menos"}}}
  (binding [i18n/*user-locale* "es"]
    (i18n/tru "must be {0} characters or less" 140)))
;; => "deben tener 140 caracteres o menos"
```

You can also bind `mr-worldwide.impl/*locales*` to mock the set of available locales.

# Reader Tags

`mr-worldwide.core/locale` is a *pretty good* function for coercing all sorts of things to a `java.util.Locale`; you
might want to consider using the using it for reader literal tag `#locale`, so you can do things like

```clj
#locale "en_US"
```

To do this: add it to a `data_readers.clj` file on your classpath:

```
{locale mr-worldwide.core/locale}
```

it's also nice to have instances of `Locale` print as

```
#locale "en_US"
```

instead of

```
#object[java.util.Locale 0x699cba07 "en_US"]
```

You can do this by defining these print methods for it:

```clj
(defmethod print-method java.util.Locale
  [d writer]
  ((get-method print-dup java.util.Locale) d writer))

(defmethod print-dup java.util.Locale
  [locale ^java.io.Writer writer]
  (.write writer "#locale ")
  (.write writer (pr-str (str locale))))
```

# License

Code, documentation, and artwork copyright © 2025 [Metabase, Inc.](https://metabase.com).

Distributed under the [Eclipse Public
License](https://raw.githubusercontent.com/metabase/mr-worldwide/master/LICENSE), same
as Clojure.
