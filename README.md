# Welcome to Quarkiverse!

<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-1-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

Congratulations and thank you for creating a new Quarkus extension project in Quarkiverse!

Feel free to replace this content with the proper description of your new project and necessary
instructions how to use and contribute to it.

You can find the basic info, Quarkiverse policies and conventions
in [the Quarkiverse wiki](https://github.com/quarkiverse/quarkiverse/wiki).

Need to quickly create a new Quarkus extension Maven project? Just execute the command below
replacing the template values with your preferred ones:

```
mvn io.quarkus:quarkus-maven-plugin:<QUARKUS_VERSION>:create-extension -N \
    -DgroupId=io.quarkiverse.<REPO_NAME> \ 
    -DartifactId=<EXTENSION_ARTIFACT_ID> \  
    -Dversion=<INITIAL_VERSION> \ 
    -Dquarkus.nameBase="<EXTENSION_SIMPLE_NAME>"
```

**IMPORTANT:** make sure your project
uses [io.quarkiverse:quarkiverse-parent](https://github.com/quarkiverse/quarkiverse-parent) as the
parent POM. It will make sure the release and artifact publishing plugins are properly configured
for your project.

In case you are creating a Quarkus extension project for the first time, please
follow [Building My First Extension](https://quarkus.io/guides/building-my-first-extension) guide.

Other useful articles related to Quarkus extension development can be found under
the [Writing Extensions](https://quarkus.io/guides/#writing-extensions) guide category on
the [Quarkus.io](http://quarkus.io) website.

Thanks again, good luck and have fun!

## Documentation

The documentation for this extension should be maintained as part of this repository and it is
stored in the `docs/` directory.

The layout should follow
the [Antora's Standard File and Directory Set](https://docs.antora.org/antora/2.3/standard-directories/)
.

Once the docs are ready to be published, please open a PR including this repository in
the [Quarkiverse Docs Antora playbook](https://github.com/quarkiverse/quarkiverse-docs/blob/master/antora-playbook.yml#L7)
. See an example [here](https://github.com/quarkiverse/quarkiverse-docs/pull/1).

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tr>
    <td align="center"><a href="http://about.me/metacosm"><img src="https://avatars.githubusercontent.com/u/120057?v=4?s=100" width="100px;" alt=""/><br /><sub><b>Chris Laprun</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-operator-sdk/commits?author=metacosm" title="Code">💻</a> <a href="#maintenance-metacosm" title="Maintenance">🚧</a></td>
  </tr>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors)
specification. Contributions of any kind welcome!