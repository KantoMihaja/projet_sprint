@echo off

REM Créer le répertoire de sortie temporaire
mkdir "out"

REM Copier tous les fichiers .java dans le répertoire de sortie temporaire
for /r "src" %%f in (*.java) do copy "%%f" "out"

REM Compiler toutes les classes en spécifiant le classpath
cd out
javac -d . *.java

cd ..
REM Créer le fichier JAR en spécifiant le point d'entrée et en incluant les fichiers compilés
jar cfe "lib\front-controller.jar" mg.p16.Spring.FrontController -C out .

REM Supprimer le répertoire de sortie temporaire
@REM if exist "out" (
@REM     rmdir /s /q "out"
@REM )

pause
