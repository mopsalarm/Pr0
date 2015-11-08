## Website

### Toolchain
Um einen Build der Website zu erstellen wird
[npm](http://blog.npmjs.org/post/85484771375/how-to-install-npm)
benötigt.  
Um dann alle zusatztools zu installieren muss einmal ```npm install``` ausgeführt werden.

### Publish
Um Änderungen zu veröffentlichen muss ein Build durchgeführt und anschließend ein push auf gh-pages durchgeführt werden. Dazu zuerst ein Build mit
```gulp publish```
und danach den aktuellen Stand commiten.
Abschließend kann das ganze mit
```sh publish.sh``` gepublisht werden.  
Letzteres Script führt ```git subtree push --prefix dist origin gh-pages``` aus.  
Damit sind die Änderungen veröffentlicht.
