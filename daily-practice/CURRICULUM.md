# 코딩 테스트 커리큘럼

## 목표
Easy 문제로 기본기를 탄탄하게 다지기

---

## 나의 현재 상황

- Python 책으로 개념은 읽어봤지만, 실제로 어떻게 적용하는지 체화되지 않음
- 코드를 작성할 때 다양한 에러가 계속 발생함
- "파이썬으로 어떻게 말하는지" 익숙해져야 함
- **목표**: Easy 문제를 반복하면서 Python 문법과 자료구조 사용법을 손에 익히기

---

## Phase 1: HashMap & Set (가장 중요!)

면접에서 가장 많이 나오는 패턴. O(n^2) → O(n) 최적화의 핵심.

| 순서 | 문제 | 핵심 개념 | 왜 중요한가 | 상태 |
|------|------|----------|------------|------|
| 1 | Two Sum | HashMap | 면접 단골 1위. "필요한 값 찾기" 패턴 | Done (1/24) |
| 2 | Valid Anagram | HashMap/정렬 | 문자 빈도수 세기 패턴 | Done (1/26) |
| 3 | Contains Duplicate | Set | 중복 체크의 기본 | In Progress |
| 4 | Single Number | XOR/Set | 비트 연산 기초 | |
| 5 | Intersection of Two Arrays | Set | 집합 연산 활용 | |
| 6 | First Unique Character | HashMap | 빈도수 + 순서 | |

---

## Phase 2: 문자열 처리

문자열 다루기, 필터링, 변환 연습.

| 순서 | 문제 | 핵심 개념 | 왜 중요한가 | 상태 |
|------|------|----------|------------|------|
| 7 | Valid Palindrome | 투포인터, 필터링 | 문자열 정제 기초 | Done (1/26) |
| 8 | Reverse String | 투포인터 | in-place 변환 | |
| 9 | Valid Parentheses | Stack | 괄호 매칭 (필수!) | |
| 10 | Longest Common Prefix | 문자열 비교 | 여러 문자열 처리 | |

---

## Phase 3: 배열 순회

배열을 한 번 순회하면서 답을 찾는 패턴.

| 순서 | 문제 | 핵심 개념 | 왜 중요한가 | 상태 |
|------|------|----------|------------|------|
| 11 | Best Time to Buy/Sell Stock | 최솟값 추적 | 면접 단골. 그리디 기초 | |
| 12 | Maximum Subarray | 카데인 알고리즘 | DP 입문 | |
| 13 | Move Zeroes | 투포인터 | in-place 이동 | |
| 14 | Plus One | 배열 조작 | 캐리 처리 | |

---

## Phase 4: 링크드리스트 기초

자료구조 이해와 포인터 조작.

| 순서 | 문제 | 핵심 개념 | 왜 중요한가 | 상태 |
|------|------|----------|------------|------|
| 15 | Reverse Linked List | 포인터 조작 | 기본 중의 기본 | |
| 16 | Merge Two Sorted Lists | 병합 | 정렬된 리스트 합치기 | |
| 17 | Linked List Cycle | 플로이드 알고리즘 | 사이클 탐지 | |

---

## Phase 5: 이진탐색 & 정렬

효율적인 탐색 방법.

| 순서 | 문제 | 핵심 개념 | 왜 중요한가 | 상태 |
|------|------|----------|------------|------|
| 18 | Binary Search | 이분탐색 | O(log n) 탐색의 기본 | |
| 19 | Search Insert Position | 이분탐색 응용 | 삽입 위치 찾기 | |
| 20 | Merge Sorted Array | 병합 정렬 | 두 배열 합치기 | |

---

## 학습 방법

1. **문제 이해** - 예제 손으로 따라해보기
2. **브루트포스 먼저** - 가장 단순한 방법으로 풀기
3. **최적화** - HashMap/Set으로 개선할 수 있는지 생각
4. **시간복잡도** - O(?) 인지 설명할 수 있어야 함

---

---

## [NEW] Implementation Mastery Roadmap (취업 집중형)

일반 기업 코테 통과를 위한 핵심 3단계 전략입니다.

### Phase 1: Fluent HashMap & String (2주)
- **목표**: 문제를 보자마자 `dict`와 `set`을 10초 안에 선언하고 사용할 수 있는 수준.
- **핵심**: Two Sum, Valid Anagram, Group Anagrams
- **훈련**: 리트코드 Easy 20문제 + Medium 5문제

### Phase 2: Simulation & Greedy (2주)
- **목표**: 긴 지문의 요구사항을 빠짐없이 코드로 옮기는 능력 (현업 코테 1-2번 유형).
- **핵심**: 상하좌우 이동(DX/DY), 행렬(Matrix) 조작, 시간/날짜 계산.
- **훈련**: 프로그래머스 Level 2 (구현/그리디 태그)

### Phase 3: BFS/DFS (2주)
- **목표**: 그래프 탐색 템플릿을 외워서 10분 안에 구현하는 수준.
- **핵심**: 연결 성분 개수, 최단 거리 기초, 모든 경로 탐색.
- **훈련**: 프로그래머스 Level 2-3 (DFS/BFS 태그)

---

## 진행 현황

- [x] Python Basics 1-8 완료
- [x] Two Sum (1/24)
- [x] Valid Palindrome (1/26)
- [x] Valid Anagram (1/26)  
- [ ] Contains Duplicate ← 현재 진행 중

## 다음 단계

1. `live-02-hashmap-set.py` 완수 (지금 바로 시작!)
2. 프로그래머스 Level 1 '해시' 문제 전수 풀이 (5문제 가량)
