# A Quarkus extension to write operators in Java

<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-3-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

This extension integrates the [Java Operator SDK](https://javaoperatorsdk.io) project (JOSDK) with Quarkus, making it
even
easier to use both. We often refer to this extension as `QOSDK` for Quarkus extension for the java Operator SDK.

## Getting started with QOSDK

### Bootstrapping a project

The easiest way to get started with QOSDK is to use the [`quarkus` CLI](https://quarkus.io/guides/cli-tooling):

```shell
quarkus create app org.acme:qosdk-test -x='qosdk'
cd qosdk-test
```

Alternatively, you can use the Quarkus Maven plugin to bootstrap a project using the extension.

```shell
mvn io.quarkus.platform:quarkus-maven-plugin:3.9.4:create \
    -DprojectGroupId=org.acme \
    -DprojectArtifactId=qosdk-test \
    -Dextensions='qosdk'
cd qoskd-test
```

If you also want to use the OLM bundle generation extension, you can add the `olm` extension to the extension list as
in:

```shell
quarkus create app org.acme:qosdk-test -x='qosdk,olm'
```

For reference, please refer to the [Quarkus getting started](https://quarkus.io/guides/getting-started) guide.

### Interactive operator development

The easiest way to iterate faster on developing your operator is to use the Quarkus Dev Mode to write your operator as
it's running.

Start the dev mode (`quarkus dev` or `mvn quarkus:dev`). Once the QOSDK extension is installed for a project, you get
access to dev mode commands provided by QOSDK to help you in moving faster. In the dev mode console, press `:` (column),
you'll get a terminal prompt. If you then press tab, you'll see a list of available commands, among which should be one
named `qosdk`, which, in turn, provides several sub-commands (`versions` and `api` at this time).

You can type `qosdk api --help` or `qosdk versions --help` to get more information about these commands.

The `qosdk api` command helps you quickly add a custom resource (an API in Kubernetes parlance) along with associated
spec, status and reconciler classes while your operator is running.

## Documentation

To go deeper on what you can accomplish with QOSDK and JOSDK, please read
the [blog series](https://developers.redhat.com/articles/2022/02/15/write-kubernetes-java-java-operator-sdk) that we
wrote
on how to write operators in Java with Quarkus. Note, however, that some information might be outdated since this series
was written a while ago. It should still provide a good idea of what can be achieved.

Please also refer to the [JOSDK documentation](https://javaoperatorsdk.io/docs/getting-started) for more details.

You can also take a look at the [samples](samples) to get quickly started using examples.

[QOSDK Documentation](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html)

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