- Instead of locales.clj we should generate a mr-worldwide-config.edn file

- Configurable resources directory, or at least something more unique e.g.
  resources/mr-worldwide/

- Get build script working, with two separate artifacts.

- Documentation.

- Tests that use real Metabase files need some replacement files

- Build needs actual e2e tests -- one for generating the POT file and one for generating resources from POs.

- Should build scripts use tools.logging instead of println/printf??

- Configurable package name instead of Metabase/metabase

- Figure out what to do about `mr-worldwide.build.create-artifacts.cljs/frontend-message?`.
