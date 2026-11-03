# 포키폰 (PokiPhone)

포키 서버용 스마트폰 모드 — [MCphone](https://github.com/november521/mcphone) (MIT, © november521) 포크.
원본 기준: november521/mcphone `0e5e275` (1.12.0). 원본 설명은 [UPSTREAM-README.md](UPSTREAM-README.md), 라이선스는 [LICENSE](LICENSE).

빌드하는 건 `platforms/1.21.1-fabric` 하나뿐 (포키 서버 = Fabric 1.21.1 / Arclight).

## 포키에서 바꾼 것
- 한국어 (`ko_kr`) — 채팅 앱 "포키톡", 폰 "포키폰"
- 친구 순간이동 항상 끔 (`ServerConfig.allowFriendTeleport`)
- 앱스토어·브라우저·스크립트 앱(.vue) 제거 (services 목록, `PhoneScreenRegistry.register`)
- 포키톡 사진은 포키폰 카메라로 찍은 것만 (`CameraPhotos`, 게임 폴더 `mcphone/camera_photos.txt`), 파일 끌어다 놓기 막음
- "나" 탭에서 GitHub·후원 제거
- (예정) 둘러보기 탭 → SNS 피드

## 배포
main 에 올리면 GitHub Actions 가 빌드해서 Releases 에 jar.
