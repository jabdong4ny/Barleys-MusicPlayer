# MusicPlayer

유튜브에서 음악을 검색하고, 화면을 꺼도 끊기지 않고 백그라운드로 재생할 수 있는 Android 앱.

## 왜 이렇게 만들었나

- YouTube 공식 API(Data API v3)는 검색 메타데이터만 주고 실제 오디오 스트림은 제공하지 않습니다. 화면이 꺼져도 끊기지 않는 재생을 하려면 실제 스트림 URL이 필요해서, **NewPipeExtractor**(NewPipe가 쓰는 추출 라이브러리)로 검색과 스트림 URL 추출을 모두 처리합니다.
- 이 방식은 YouTube 이용약관상 **비공식**입니다. YouTube 쪽 구현이 바뀌면 검색/재생이 갑자기 안 될 수 있고, 라이브러리 업데이트로 대응해야 합니다. 개인 용도로 흔히 쓰이는 방식이지만, 공식적으로 허용된 방법은 아니라는 점을 알고 쓰세요.
- 백그라운드 재생/알림 컨트롤(재생바)은 Media3의 `MediaSessionService` + `ExoPlayer`로 구현했습니다. 이게 Android에서 음악 앱이 화면 꺼진 상태로 재생을 유지하는 표준 방법입니다.

## 스택

- Kotlin + Jetpack Compose + Material3
- Hilt (DI)
- Media3 ExoPlayer + MediaSession — 백그라운드 재생, 알림 컨트롤
- NewPipeExtractor — YouTube 검색 + 오디오 스트림 URL 추출 (비공식)
- Coil — 썸네일 이미지 로딩

## 동작 방식

1. 검색창에 검색어를 입력하면 `YoutubeExtractorService`가 NewPipeExtractor로 YouTube 검색 결과(제목/아티스트/썸네일/길이)를 가져옵니다.
2. 결과 목록에서 곡을 탭하면, 탭한 곡부터 시작하는 재생목록이 `MediaController`를 통해 `MusicPlaybackService`(MediaSessionService)로 전달됩니다.
3. 각 곡의 실제 오디오 스트림 URL은 **재생 바로 직전에** `YoutubeDataSourceResolver`가 지연 추출합니다. (미리 다 추출해두면 스트림 URL이 만료될 수 있어서, ExoPlayer가 그 곡을 틀기 직전에만 추출합니다)
4. 화면을 끄거나 앱을 백그라운드로 보내도 포그라운드 서비스 + 알림의 재생/일시정지/다음곡 버튼으로 계속 들을 수 있습니다.
5. 재생목록은 검색 결과 리스트 그 자체라서, 다음곡/이전곡으로 검색 결과를 순서대로 넘겨 들을 수 있습니다.

## 빌드하는 방법

Firebase 등 별도 설정이 필요 없습니다. Android Studio에서 `Barley-s-MusicPlayer` 폴더를 열고 Gradle sync 후 실행하면 바로 됩니다.

## 알려진 제약 (MVP 범위)

- **비공식 추출 방식**: 위에서 설명한 대로 YouTube 쪽 변경에 취약합니다. 검색이나 재생이 안 되면 NewPipeExtractor 라이브러리 버전을 최신으로 올려보세요.
- **단일 재생목록**: 검색 결과 리스트가 곧 재생목록입니다. 즐겨찾기, 플레이리스트 저장, 재생 기록 같은 기능은 없습니다.
- **라이브 스트림/프리미어 미지원**: 일반 동영상의 오디오만 재생합니다.
- **네트워크 의존**: 스트림을 캐싱하지 않아서, 오프라인 재생은 안 됩니다.
