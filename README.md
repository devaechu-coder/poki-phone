# 포키폰 (PokiPhone)

포키 서버용 스마트폰 모드 — [MCphone](https://github.com/november521/mcphone) (MIT, © november521) 포크.
원본 기준: november521/mcphone `0e5e275` (1.12.0). 원본 설명은 [UPSTREAM-README.md](UPSTREAM-README.md), 라이선스는 [LICENSE](LICENSE).

빌드하는 건 `platforms/1.21.1-fabric` 하나뿐 (포키 서버 = Fabric 1.21.1 / Arclight).

## 포키에서 바꾼 것
- 한국어 (`ko_kr`) — 채팅 앱 "포키톡", 폰 "포키폰"
- 친구 순간이동 항상 끔 (`ServerConfig.allowFriendTeleport`)
- 앱스토어·브라우저·스크립트 앱(.vue)·음악·책(리더)·엔더상자·웨이스톤 제거 (외부 파일 가져오기 막기) (services 목록, `PhoneScreenRegistry.register`)
- 포키톡 사진은 포키폰 카메라로 찍은 것만 (`CameraPhotos`, 게임 폴더 `mcphone/camera_photos.txt`), 파일 끌어다 놓기 막음
- "나" 탭에서 GitHub·후원 제거
- 포키냥 이모티콘 17종 (`PokiSticker`, 그림은 `textures/poki_sticker`, 메시지는 `[[포키냥:N]]` 글자만 오감 · 입력칸 웃는 얼굴 버튼)
- 카메라 키: 찍기 Enter, 나가기 Backspace (원래 V·X 는 음성채팅 등과 겹침, 키 이름을 바꿔 예전 저장값 무시)
- 이름은 PokiName 한글닉으로 표시 (`PokiNames`, 서버의 `plugins/PokiName/nicknames.yml` 을 읽기만 함, 친구 추가 검색은 마크닉)
- 「SNS」 탭(원래 둘러보기) = 포키 SNS · 차단 해제는 「내 글 → 차단 목록」 (`feature/sns`, 화면 `ChatFeed`)
  - 글 300자 + 카메라 사진 1장 (사진은 채팅 사진 업로드를 예약 대상 `SnsWire.PEER` 로 재사용), 좋아요, 댓글 200자
  - 5일 지나면 글·사진 자동 삭제, 욕설 필터(`SnsFilter`), 글 1분 1개·하루 10개, 댓글 5초
  - 신고 3회 → 피드에서 숨김 + 운영진 검토함. 차단한 사람 글·댓글은 안 보임
  - 운영진(OP): `/포키폰 sns 검토함|목록|복구 <앞8자리>|삭제 <앞8자리>|정지 <닉>|정지해제 <닉>` (영문 `/pokiphone sns review|list|restore|delete|mute|unmute`)
  - 저장: 월드 `data/mcphone_sns.dat`, 사진은 `mcphone/chat-images`

## 배포
main 에 올리면 GitHub Actions 가 빌드해서 Releases 에 jar.
