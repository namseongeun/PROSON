package com.ssafy.prosn.service;

import com.ssafy.prosn.domain.post.*;
import com.ssafy.prosn.domain.user.User;
import com.ssafy.prosn.dto.*;
import com.ssafy.prosn.exception.BadRequestException;
import com.ssafy.prosn.exception.NotAccessUserException;
import com.ssafy.prosn.repository.post.InformationRepository;
import com.ssafy.prosn.repository.post.LikeDislikeRepository;
import com.ssafy.prosn.repository.post.PostRepository;
import com.ssafy.prosn.repository.post.tag.PostTagRepository;
import com.ssafy.prosn.repository.post.tag.TagRepository;
import com.ssafy.prosn.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * created by seongmin on 2022/07/25
 * updated by seongmin on 2022/08/08
 */
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostTagRepository postTagRepository;
    private final TagRepository tagRepository;
    private final LikeDislikeRepository likeDislikeRepository;
    private final ProblemRepository problemRepository;
    private final InformationRepository informationRepository;

    @Override
    @Transactional
    public Post writeProblem(ProblemRequestDto problemDto, Long uid) {
        User user = userRepository.findById(uid).orElseThrow(() -> new BadRequestException("유효하지 않은 사용자입니다."));
        Problem problem = Problem.builder()
                .user(user)
                .title(problemDto.getTitle())
                .answer(problemDto.getAnswer())
                .example1(problemDto.getEx1())
                .example2(problemDto.getEx2())
                .example3(problemDto.getEx3())
                .example4(problemDto.getEx4())
                .mainText(problemDto.getMainText())
                .build();
        savePost(problemDto, problem);
        return problem;
    }

    @Override
    @Transactional
    public Post writeInformation(InformationRequestDto informationDto, Long uid) {
        User user = userRepository.findById(uid).orElseThrow(() -> new BadRequestException("유효하지 않은 사용자입니다."));
        Information information = Information.builder()
                .user(user)
                .mainText(informationDto.getMainText())
                .title(informationDto.getTitle())
                .build();

        savePost(informationDto, information);
        return information;

    }

    @Override
    @Transactional
    public void delete(Long id, Long uid) {
        Post post = postRepository.findById(id).orElseThrow(() -> new BadRequestException("유효하지 않은 게시글입니다."));
        if (!post.getUser().getId().equals(uid))
            throw new NotAccessUserException("내가 쓴 게시글만 삭제 가능합니다.");

        post.remove();
    }

    /**
     * @param id //problem id
     * @return problem detail
     * 삭제된 게시글일 경우 예외던짐. (문제집에서 확인할 때는 안던지도록 처리필요)
     */
    @Override
    public PostDetailResponseDto showProblemDetail(Long id) {
        Post post = postRepository.findById(id).orElseThrow(() -> new BadRequestException("유효하지 않은 게시글입니다."));
        log.info("post type = {}", post.getClass());
        if (post instanceof Problem) {
            log.info("문제 디테일");
            Problem problem = (Problem) post;
            if (problem.isDeleted()) throw new BadRequestException("삭제된 게시글입니다.");
            return ProblemDetailResponseDto.builder()
                    .title(problem.getTitle())
                    .user(new UserResponseDto(problem.getUser().getId(), problem.getUser().getName()))
                    .id(problem.getId())
                    .mainText(problem.getMainText())
                    .answer(problem.getAnswer())
                    .example1(problem.getExample1())
                    .example2(problem.getExample2())
                    .example3(problem.getExample3())
                    .example4(problem.getExample4())
                    .comments(problem.getComments())
                    .numOfLikes(getNumOfLikes(problem))
                    .numOfDislikes(getNumOfDislikes(problem))
                    .views(problem.getViews())
                    .tags(getTags(problem))
                    .type(PostType.PROBLEM)
                    .build();
        } else if (post instanceof Information) {
            log.info("정보 디테일");
            Information information = (Information) post;
            if (information.isDeleted()) throw new BadRequestException("삭제된 게시글입니다.");

            return InformationDetailResponseDto.builder()
                    .id(information.getId())
                    .user(new UserResponseDto(information.getUser().getId(), information.getUser().getName()))
                    .mainText(information.getMainText())
                    .comments(information.getComments())
                    .numOfDislikes(getNumOfDislikes(information))
                    .numOfLikes(getNumOfLikes(information))
                    .tags(getTags(information))
                    .views(information.getViews())
                    .type(PostType.INFORMATION)
                    .build();
        } else {
            log.info("그외(문제집) 디테일");
            return null;
        }
    }

    @Override
    public PostResponseDto showAllPost(Pageable pageable) {
//        List<Post> posts = postRepository.findAll();
//        List<PostAllResponseDto> result = new ArrayList<>();
//        posts.forEach(post -> {
//            result.add(new PostAllResponseDto(post.getId(),
//                    new UserResponseDto(post.getUser().getId(),
//                            post.getUser().getName()),
//                    post.getTitle(),
//                    post.getViews(),
//                    getNumOfLikes(post),
//                    getNumOfDislikes(post)));
//        });
        Page<Post> posts = postRepository.findByIsDeleted(false, pageable);
        PostResponseDto result = new PostResponseDto();
        result.addPost(posts.getContent(), posts.getTotalPages(), posts.getTotalElements());
        return result;
    }

    @Override
    public PostResponseDto showAllProblem(Pageable pageable) {
        Page<Problem> problems = problemRepository.findByIsDeleted(false, pageable);
        PostResponseDto result = new PostResponseDto();
        result.addPost(problems.getContent(), problems.getTotalPages(), problems.getTotalElements());
        return result;
    }

    @Override
    public PostResponseDto showAllInformation(Pageable pageable) {
        Page<Information> information = informationRepository.findByIsDeleted(false, pageable);
        PostResponseDto result = new PostResponseDto();
        result.addPost(information.getContent(), information.getTotalPages(), information.getTotalElements());
        return result;
    }

    @Override
    @Transactional
    public void likeDislikeClick(LikeDisLikeRequestDto dto, Long uid) {
        User user = userRepository.findById(uid).orElseThrow(() -> new BadRequestException("유효하지 않은 사용자입니다."));
        Post post = postRepository.findById(dto.getPid()).orElseThrow(() -> new BadRequestException("유효하지 않은 게시글입니다."));

        Optional<LikeDislike> result = likeDislikeRepository.findByPostAndUser(post, user);
        if (result.isPresent()) {
            if (result.get().isType() == dto.isType()) { // 예전에 누른거랑 같은버튼 누른 경우 삭제
                likeDislikeRepository.delete(result.get());
            } else { // 예전에 누른거랑 반대버튼 누른 경우 체인지 좋<->싫
                result.get().change();
            }
        } else {
            likeDislikeRepository.save(new LikeDislike(user, post, dto.isType()));
        }
    }

    @Override
    public List<PostAllResponseDto> searchPost(PostSearchRequestDto dto) {
        List<PostTag> postTags = postRepository.searchPost(dto.getTitle(), dto.getCode());
        List<PostAllResponseDto> result = new ArrayList<>();
        for (PostTag postTag : postTags) {
            PostAllResponseDto post = new PostAllResponseDto(
                    postTag.getPost().getId(),
                    new UserResponseDto(postTag.getPost().getUser().getId(), postTag.getPost().getUser().getName()),
                    postTag.getPost().getTitle(),
                    postTag.getPost().getViews(),
                    postTag.getPost().getNumOfLikes(),
                    postTag.getPost().getNumOfDislikes());
            result.add(post);
        }
        return result;
    }

    private List<Tag> getTags(Post post) {
        List<PostTag> postTagByPost = postTagRepository.findPostTagByPost(post);
        List<Tag> tags = new ArrayList<>();
        postTagByPost.forEach(postTag -> {
            tags.add(postTag.getTag());
        });
        return tags;
    }

    private Long getNumOfLikes(Post post) {
        return likeDislikeRepository.countByPostAndType(post, true);
    }

    private Long getNumOfDislikes(Post post) {
        return likeDislikeRepository.countByPostAndType(post, false);
    }

    private void savePost(PostRequestDto postDto, Post post) {
        postRepository.save(post);
        postDto.getTags().forEach(code -> {
            Optional<Tag> tag = tagRepository.findByCode(code);
            tag.orElseThrow(() -> new BadRequestException("유효하지 않은 태그입니다."));
            post.addPostTag(new PostTag(post, tag.get()));

//            postTagRepository.save(new PostTag(post, tag.get()));
        });
    }
}
