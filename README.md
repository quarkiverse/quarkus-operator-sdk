# A Quarkus extension to write operators in Java

<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-3-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

See [samples](samples) to get quickly started using an example

## [Documentation](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html)

### Maintaining the documentation

The documentation for this extension should be maintained as part of this repository and it is
stored in the `docs/` directory.

The layout should follow
the [Antora's Standard File and Directory Set](https://docs.antora.org/antora/2.3/standard-directories/)
.

Once the docs are ready to be published, please open a PR including this repository in
the [Quarkiverse Docs Antora playbook](https://github.com/quarkiverse/quarkiverse-docs/blob/master/antora-playbook.yml#L7)
. See an example [here](https://github.com/quarkiverse/quarkiverse-docs/pull/1).

## Releasing

Follow the [Quarkiverse release process](https://github.com/quarkiverse/quarkiverse/wiki/Release).

## Snapshots

A snapshot is generated each time the `main` or `next` branches are changed. To be able to use the
snapshots, please add the following repository definition to your POM file (in the `repositories`
section) or, preferably, to your `settings.xml` file:

```xml
    <repositories>
        ...
        <repository>
            <id>s01.oss.sonatype</id>
            <url>https://s01.oss.sonatype.org/content/repositories/snapshots/</url>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>
```

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tr>
    <td align="center"><a href="https://about.me/metacosm"><img src="https://avatars.githubusercontent.com/u/120057?v=4?s=100" width="100px;" alt=""/><br /><sub><b>Chris Laprun</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-operator-sdk/commits?author=metacosm" title="Code">💻</a> <a href="#maintenance-metacosm" title="Maintenance">🚧</a></td>
    <td align="center"><a href="https://www.inulogic.fr"><img src="https://avatars.githubusercontent.com/u/88554524?v=4?s=100" width="100px;" alt=""/><br /><sub><b>Sébastien CROCQUESEL</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-operator-sdk/commits?author=scrocquesel" title="Code">💻</a></td>
    <td align="center"><a href="https://sgitario.github.io/about/"><img src="https://avatars.githubusercontent.com/u/6310047?v=4?s=100" width="100px;" alt=""/><br /><sub><b>Jose Carvajal</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-operator-sdk/commits?author=Sgitario" title="Code">💻</a> <a href="#ideas-Sgitario" title="Ideas, Planning, & Feedback">🤔</a></td>
  </tr>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors)
specification. Contributions of any kind welcome!