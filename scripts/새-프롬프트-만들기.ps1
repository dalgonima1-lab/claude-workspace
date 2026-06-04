# 새 프롬프트 파일을 prompts/ 폴더에 빠르게 만드는 스크립트
# 사용법: .\scripts\새-프롬프트-만들기.ps1 "파일명"
# 예시:  .\scripts\새-프롬프트-만들기.ps1 "번역-요청"

param(
    [Parameter(Mandatory=$true)]
    [string]$Name
)

$path = "prompts\$Name.md"

if (Test-Path $path) {
    Write-Host "이미 존재하는 파일입니다: $path" -ForegroundColor Yellow
    exit 1
}

$today = Get-Date -Format "yyyy-MM-dd"
$content = @"
# $Name

생성일: $today

## 사용법
아래 템플릿을 Claude에게 붙여넣으세요.

---

[여기에 프롬프트 내용 작성]
"@

Set-Content -Path $path -Value $content -Encoding UTF8
Write-Host "생성 완료: $path" -ForegroundColor Green
