### Import and Export data sources

This document shows how to use the VDB Builder cli to import and export data sources interactively.  You can use this sample as a starting point for working with your own data sources.

Note that in __VDB Builder__, you can use __tab completion__ to see the available commands options, or use __help commandName__ to see command details.


### Requirements

* Install VDB Builder cli - refer to the [Installation Instructions](install-cli.md) for details


### Import and Export data sources

The sample session below shows how to import and export data sources.  The session first shows export of __ExampleDS__ to file __./mySources.xml__ .  Then the workspace datasource is deleted and the file __./mySources.xml__ is imported.

![Import-Export VDB Session](img/cli-import-export-datasource.png)

---
```
export-datasource <datasource-name> <datasource-file>
```
export the specified data source <datasource-name> to the specified file <datasource-file>

```
upload-datasource <datasource-file>
``` 
upload datasource(s) from <datasource-file> into the workspace

---


