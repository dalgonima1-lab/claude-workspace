`biz-assistant.keystore.enc` 는 앱 서명키를 AES-256으로 암호화한 파일이다.
GitHub Actions가 저장소 비밀 `KEYSTORE_PASSPHRASE` 로 풀어 서명한다. 원본 키 파일은 저장소에 올리지 않는다.
같은 키로 서명해야 설치된 앱 위에 업데이트가 된다. 키를 바꾸면 기존 앱을 지우고 새로 설치해야 한다.
