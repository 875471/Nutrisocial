# Fotos para la evaluación del OCR (CER/WER)

Un par de archivos por receta, con el mismo nombre:

- `receta01.jpg` (o `.jpeg`, `.png`): la foto, tal cual sale de la cámara del móvil.
- `receta01.txt`: la transcripción correcta, a mano, en UTF-8.

Reglas para transcribir (ver informe 17):

1. Copiar exactamente lo que está escrito, con sus mayúsculas, tildes, números y signos, aunque
   tenga faltas de ortografía: se mide si ML Kit lee lo que hay, no si la receta está bien escrita.
2. Una línea del archivo por cada línea escrita en el papel, en el orden en que se lee
   (de arriba abajo; con dos columnas, primero la izquierda entera y después la derecha).
3. Sin líneas en blanco de más ni texto que no esté en la foto (nada de "Título:" o comentarios).
4. Las palabras tachadas no se transcriben.

Ejecutar: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.nutrisocial.OcrRecognitionEvalTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`

Este archivo no es una imagen y la prueba lo ignora.
