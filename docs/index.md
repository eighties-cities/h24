# Home

here you can fin

  - la doc et les principes un peu expliqué du code source d'h24 (ici)
  - un exemple tutorial (déjà écrit par julien) 
  - etc.

Pour la doc du code source, c'est un *work in progress* :factory_worker: à partir des infos déjà écrites [ici](https://hackmd.io/IGeOfl9yQmmkekJzgTClMA?both)


!!! note "info editeur"

    - Pour modifier les options et la navigation du site, tout est dans le `../mkdocs.yml`
    - Pour écrire du code source "inline", par exemple, en python : `#!python range()` est la méthode qui sert à générer des séquences.
    - Pour écrire des bouts de code, voir dans workflow.md pour la fonction `code_from_file(...)` 
    - Pour les infobox, les icones/emoji, etc. voir la [documentation](https://squidfunk.github.io/mkdocs-material/reference/admonitions/)



## About

Le site est fait avec [MkDocs](https://www.mkdocs.org/), et l'extension [material](https://squidfunk.github.io/mkdocs-material/).

Il est compilé via une github action, voir dans le folder `.github/workflows/mail.yml`

La documentation est une primo-étape souvent oublié vers la reproductibilité. La todo list pour une vrai reproductibilité qui permettrait ensuite de s'autonomiser vis à vis des outils actuels dev instable (python, scala, github api, mkdocs api, etc. ) est ensuite longue... 


En attendant la sortie de [Dottydoc](https://dotty.epfl.ch/docs/usage/dottydoc.html), qui permettra d'extraire facilement des signatures `.scala` pour les convertir au format markdown, le site utilise une bête macro en python, avec sed et l'injecter dans le code.

La macro (`/docs/macro.py`) en question s'appuie sur un plugin mkdocs [macro](https://github.com/fralau/mkdocs_macros_plugin#macros-and-filters). 

Les plugins pour mkdocs utilisé [la doc de mkdocs](https://squidfunk.github.io/mkdocs-material/reference/abbreviations/) sont listés dans le .yml : inlinehilite, highlight, emoji
