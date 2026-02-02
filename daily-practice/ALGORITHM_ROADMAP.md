# 알고리즘 학습 로드맵

코딩 테스트를 위한 체계적인 학습 가이드

---

## 1단계: Python 기본 문법 (현재 위치!)

코드를 "말하는" 법을 익히는 단계. 이게 끝나면 Easy 문제 풀 수 있음.

### 필수 개념

| 개념 | 설명 | 체크 |
|------|------|------|
| **변수와 타입** | int, str, list, dict, set, bool | [ ] |
| **조건문** | if, elif, else | [ ] |
| **반복문** | for, while, break, continue | [ ] |
| **리스트** | append, pop, 슬라이싱, 인덱싱 | [ ] |
| **딕셔너리** | 키-값, get(), items(), in | [ ] |
| **Set** | 중복 제거, 교집합/합집합 | [ ] |
| **문자열** | split(), join(), 슬라이싱, isdigit() | [ ] |
| **함수** | def, return, 매개변수 | [ ] |

### 핵심 패턴

```python
# 1. 리스트 순회
for item in lst:
    
# 2. 인덱스와 함께 순회
for i, item in enumerate(lst):
    
# 3. 딕셔너리 카운팅
count = {}
for item in lst:
    count[item] = count.get(item, 0) + 1
    
# 4. 조건에 맞는 것만 모으기
result = []
for item in lst:
    if 조건:
        result.append(item)
```

### 연습 문제
- python-basics-practice-1~5.ipynb

---

## 2단계: Easy 문제 정복

기본 자료구조와 간단한 알고리즘. 면접 기초.

### 필수 자료구조

| 자료구조 | 용도 | 주요 연산 |
|----------|------|----------|
| **Array/List** | 순서 있는 데이터 | O(1) 접근, O(n) 삽입/삭제 |
| **HashMap/Dict** | 키-값 쌍 | O(1) 조회/삽입 |
| **Set** | 중복 없는 집합 | O(1) 조회, 교집합/합집합 |
| **Stack** | LIFO (후입선출) | push, pop |

### 필수 알고리즘

| 알고리즘 | 대표 문제 | 핵심 아이디어 |
|----------|----------|--------------|
| **HashMap 활용** | Two Sum | 필요한 값을 저장해두고 찾기 |
| **빈도수 카운팅** | Valid Anagram | 딕셔너리로 개수 세기 |
| **투 포인터** | Valid Palindrome | 양 끝에서 좁혀오기 |
| **그리디** | Best Time to Buy/Sell | 지금까지 최소/최대 추적 |

### 목표 문제 (12개)
1. Two Sum
2. Valid Anagram
3. Contains Duplicate
4. Single Number
5. Valid Palindrome
6. Valid Parentheses
7. Best Time to Buy and Sell Stock
8. Maximum Subarray
9. Move Zeroes
10. Majority Element
11. First Unique Character
12. Intersection of Two Arrays

### 연습 문제
- leetcode-easy-collection.ipynb
- CURRICULUM.md의 Phase 1-3

---

## 3단계: Medium 입문

Easy를 편하게 풀면 도전. 패턴이 중요해짐.

### 추가 자료구조

| 자료구조 | 용도 | 언제 쓰나 |
|----------|------|----------|
| **Queue** | FIFO (선입선출) | BFS, 순서대로 처리 |
| **Heap** | 우선순위 큐 | 최대/최소 빠르게 찾기 |
| **Linked List** | 노드 연결 | 삽입/삭제 빈번할 때 |

### 필수 알고리즘

| 알고리즘 | 대표 문제 | 시간복잡도 |
|----------|----------|-----------|
| **Binary Search** | Search in Rotated Array | O(log n) |
| **Sliding Window** | Longest Substring Without Repeat | O(n) |
| **BFS/DFS** | Number of Islands | O(V+E) |
| **Dynamic Programming** | Climbing Stairs | O(n) |

### 목표 문제
- Binary Search
- 3Sum
- Longest Substring Without Repeating Characters
- Container With Most Water
- Group Anagrams
- Coin Change
- Number of Islands

---

## 4단계: Medium 숙련

면접 대부분이 이 수준. 패턴 조합 능력 필요.

### 고급 알고리즘

| 알고리즘 | 설명 |
|----------|------|
| **Backtracking** | 모든 경우의 수 탐색 (가지치기) |
| **Graph** | BFS/DFS 심화, 최단 경로 |
| **Tree** | 순회, LCA, BST |
| **DP 심화** | 2D DP, 상태 압축 |

### 목표 문제
- Subsets
- Permutations
- Word Search
- Course Schedule
- Longest Increasing Subsequence
- Decode Ways

---

## 5단계: Hard 도전

대기업 상위권, 알고리즘 대회 수준.

### 고급 주제

- Trie (문자열 검색)
- Segment Tree (구간 쿼리)
- Union-Find (집합 병합)
- A* 알고리즘
- 비트마스킹

---

## 현실적인 학습 계획

### 신입 취준생 기준

| 기간 | 목표 | 하루 시간 |
|------|------|----------|
| 1-2주 | 1단계 완료 | 1-2시간 |
| 3-4주 | 2단계 Easy 20문제 | 1-2시간 |
| 5-8주 | 3단계 Medium 입문 30문제 | 2시간 |
| 9-12주 | 4단계 Medium 50문제 | 2시간 |

### 현재 당신의 위치

```
[1단계] ████████░░ 80% 완료
        ↑ 현재 여기!
        
[2단계] ██░░░░░░░░ 20% (Two Sum, Valid Anagram 등)

[3단계] ░░░░░░░░░░ 아직

[4단계] ░░░░░░░░░░ 아직
```

---

## 다음 단계 행동

### 지금 해야 할 것
1. `python-basics-practice-5.ipynb` 끝내기
2. `leetcode-easy-collection.ipynb` 1~4번 풀기
3. CURRICULUM.md Phase 1 완료하기

### 1주 후 목표
- Easy 10문제 풀기
- 딕셔너리 패턴 완전히 익히기

### 1달 후 목표
- Easy 20문제 완료
- Medium 첫 문제 도전

---

## 핵심 조언

### 문제 풀이 순서
1. **문제 읽기** - 예제 손으로 따라하기
2. **브루트포스** - 가장 단순한 방법으로 먼저
3. **최적화** - HashMap, Set으로 개선
4. **시간복잡도** - O(?) 설명할 수 있어야

### 막힐 때
- 15분 고민해도 모르면 → 힌트 보기
- 30분이면 → 정답 보고 이해하기
- 정답 봐도 모르면 → 더 쉬운 문제로 돌아가기

### 가장 중요한 것
> **같은 문제를 3번 풀어라**
> 1. 처음: 어떻게든 풀기
> 2. 1주 후: 다시 풀기 (기억 안 나도 OK)
> 3. 1달 후: 5분 안에 풀기

---

## 참고 자료

### 추천 사이트
- [LeetCode](https://leetcode.com) - 필수
- [NeetCode](https://neetcode.io) - 로드맵/영상 설명
- [프로그래머스](https://programmers.co.kr) - 한글 문제

### 추천 유튜브
- NeetCode (영어)
- 코딩테스트 (한국어)
