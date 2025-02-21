## Config reference:

```clj
{:locales-directory         "/home/cam/metabase/locales"
 :target-directory          "/home/cam/metabase/resources"
 :frontend-target-directory "/home/cam/metabase/resources/frontend_client/app/locales"
 :backend-target-directory  "/home/cam/metabase/resources/i18n"
 :source-paths              ["/src"
                             "/enterprise/backend/src"
                             "/modules/drivers/bigquery-cloud-sdk/src"
                             "/modules/drivers/druid/src"
                             "/modules/drivers/druid-jdbc/src"
                             "/modules/drivers/mongo/src"
                             "/modules/drivers/oracle/src"
                             "/modules/drivers/presto-jdbc/src"
                             "/modules/drivers/redshift/src"
                             "/modules/drivers/snowflake/src"
                             "/modules/drivers/sparksql/src"
                             "/modules/drivers/sqlite/src"
                             "/modules/drivers/sqlserver/src"
                             "/modules/drivers/vertica/src"]
 :overrides [{:file "/src/metabase/analyze/fingerprint/fingerprinters.clj"
               :message "Error generating fingerprint for {0}"}]}
```
