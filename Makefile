.PHONY: build install flash format check lint push-model clean

build:
	./gradlew assembleDebug

install flash:
	./gradlew installDebug

format:
	./gradlew ktlintFormat

check:
	./gradlew ktlintCheck

lint:
	./gradlew detekt

pre-commit:
	./gradlew ktlintFormat detekt assembleDebug

push-model:
	adb push gemma-3-270m-it-int8.litertlm /data/local/tmp/gemma-3-270m-it-int8.litertlm

clean:
	./gradlew clean
