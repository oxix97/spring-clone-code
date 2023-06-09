package com.example.noticeboard.service;

import com.example.noticeboard.domain.Article;
import com.example.noticeboard.domain.Hashtag;
import com.example.noticeboard.domain.UserAccount;
import com.example.noticeboard.domain.type.SearchType;
import com.example.noticeboard.dto.ArticleDto;
import com.example.noticeboard.dto.ArticleUpdateDto;
import com.example.noticeboard.dto.ArticleWithCommentDto;
import com.example.noticeboard.dto.HashtagDto;
import com.example.noticeboard.repository.ArticleRepository;
import com.example.noticeboard.repository.HashtagRepository;
import com.example.noticeboard.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional
@Slf4j
public class ArticleService {
    private final ArticleRepository repository;
    private final UserAccountRepository userAccountRepository;
    private final HashtagRepository hashtagRepository;
    private final HashtagService hashtagService;

    @Transactional(readOnly = true)
    public Page<ArticleDto> searchArticles(SearchType searchType, String searchKeyword, Pageable pageable) {
        if (searchKeyword == null || searchKeyword.isBlank()) {
            return repository.findAll(pageable).map(ArticleDto::from);
        }
        return switch (searchType) {
            case TITLE -> repository.findByTitleContaining(searchKeyword, pageable).map(ArticleDto::from);
            case CONTENT -> repository.findByContentContaining(searchKeyword, pageable).map(ArticleDto::from);
            case ID -> repository.findByUserAccount_UserIdContaining(searchKeyword, pageable).map(ArticleDto::from);
            case NICKNAME ->
                    repository.findByUserAccount_NicknameContaining(searchKeyword, pageable).map(ArticleDto::from);
            case HASHTAG ->
                    repository.findByHashtagNames(Arrays.stream(searchKeyword.split(" ")).toList(), pageable).map(ArticleDto::from);
        };
    }

    @Transactional(readOnly = true)
    public Page<ArticleDto> searchArticlesViaHashtag(String hashtagNames, Pageable pageable) {
        if (hashtagNames == null || hashtagNames.isBlank()) {
            return Page.empty(pageable);
        }

        return repository.findByHashtagNames(List.of(hashtagNames), pageable)
                .map(ArticleDto::from);
    }

    @Transactional(readOnly = true)
    public ArticleDto getArticle(Long articleId) {
        return repository.findById(articleId)
                .map(ArticleDto::from)
                .orElseThrow(() -> new EntityNotFoundException("게시글이 없습니다 - articleId: " + articleId));
    }

    @Transactional(readOnly = true)
    public ArticleWithCommentDto getArticleWithComments(Long articleId) {
        return repository.findById(articleId)
                .map(ArticleWithCommentDto::from)
                .orElseThrow(() -> new EntityNotFoundException("게시글이 없습니다 - articleId: " + articleId));
    }

    public void saveArticle(ArticleDto dto) {
        UserAccount userAccount = userAccountRepository.getReferenceById(dto.userAccountDto().userId());
        Set<Hashtag> hashtags = renewHashtagsFromContent(dto.content());

        Article article = dto.toEntity(userAccount);
        article.addHashtags(hashtags);

        repository.save(article);
    }

    public void updateArticle(Long id, ArticleDto dto) {
        try {
            Article article = repository.getReferenceById(id);
            UserAccount userAccount = userAccountRepository.getReferenceById(dto.userAccountDto().userId());

            if (article.getUserAccount().equals(userAccount)) {
                if (dto.title() != null) article.setTitle(dto.title());
                if (dto.content() != null) article.setContent(dto.content());

                Set<Long> hashtagIds = article.getHashtags().stream()
                        .map(Hashtag::getId)
                        .collect(Collectors.toUnmodifiableSet());
                article.clearHashtags();
                repository.flush();

                hashtagIds.forEach(hashtagService::deleteHashtagWithoutArticles);

                Set<Hashtag> hashtags = renewHashtagsFromContent(dto.content());
                article.addHashtags(hashtags);
            }
        } catch (EntityNotFoundException e) {
            log.info("게시글 업데이트 실패. 게시글을 찾을 수 없습니다. - dto : {}", e.getLocalizedMessage());
        }
    }


    public void deleteArticle(long articleId, String userId) {
        Article article = repository.getReferenceById(articleId);
        Set<Long> hashtagIds = article.getHashtags().stream()
                .map(Hashtag::getId)
                .collect(Collectors.toUnmodifiableSet());

        repository.deleteByIdAndUserAccount_UserId(articleId, userId);
        repository.flush();

        hashtagIds.forEach(hashtagService::deleteHashtagWithoutArticles);
    }

    @Transactional(readOnly = true)
    public List<String> getHashtags() {
        return hashtagRepository.findAllHashtagNames();
    }

    @Transactional(readOnly = true)
    public long getArticleCount() {
        return repository.count();
    }


    private Set<Hashtag> renewHashtagsFromContent(String content) {
        Set<String> hashtagNamesInContent = hashtagService.parseHashtagNames(content); // 해시태그 형식 파싱
        Set<Hashtag> hashtags = hashtagService.findHashtagsByNames(hashtagNamesInContent); //
        Set<String> existingHashtagsNames = hashtags.stream()
                .map(Hashtag::getHashtagName)
                .collect(Collectors.toUnmodifiableSet());

        hashtagNamesInContent.forEach(name -> {
            if (!existingHashtagsNames.contains(name)) {
                hashtags.add(Hashtag.of(name));
            }
        });

        return hashtags;
    }
}
