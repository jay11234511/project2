package Jay.BoardP.controller;


import static org.springframework.util.StringUtils.hasText;

import Jay.BoardP.controller.dto.MemberFormDto;
import Jay.BoardP.domain.Member;
import Jay.BoardP.service.EmailService;
import Jay.BoardP.service.memberService;
import java.time.Duration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final memberService memberService;
    private final EmailService emailService;
    private final RedisTemplate redisTemplate;


    @GetMapping("/signUp")
    public String addForm(@ModelAttribute("memberFormDto") MemberFormDto memberFormDto, Model model
    ) {
        return "members/createMemberform";
    }


    @PostMapping("/signUp")
    public String addMember(@Validated@ModelAttribute("memberFormDto") MemberFormDto memberFormDto,
        BindingResult bindingResult, HttpServletRequest request) {

//        HttpSession session1 = request.getSession();
//        String code = (String) session1.getAttribute("email");

        String code = String.valueOf(redisTemplate.opsForValue().get(memberFormDto.getEmail()));


        //비밀번호 재확인 검증
        if (!memberFormDto.getPassword().equals(memberFormDto.getCheckPassword())) {
            bindingResult.reject("checkPassword", "비밀번호가 일치하지 않습니다");
        }
        //이메일코드 인증
        if (code == null || !code.equals(memberFormDto.getCode())) {
            bindingResult.reject("checkCode", "이메일 인증번호가 일치하지 않습니다");
        }

        if (bindingResult.hasErrors()) {
            System.out.println("bindingResult = " + bindingResult);
            return "members/createMemberForm";
        }

        memberService.save(memberFormDto);

        makeUpdateCount();
//
//        HttpSession session = request.getSession();
//        session.invalidate();

        redisTemplate.delete(memberFormDto.getEmail());

        return "redirect:/";
    }

    @GetMapping("/{id}/release")
    public String releaseHumanForm(@PathVariable String id) {
        return "members/validateMember";
    }

    @PostMapping("/{id}/release")
    public String validateHuman(@PathVariable Long id, @RequestParam String password , RedirectAttributes redirectAttributes) {

        if (!hasText(password)) {
            redirectAttributes.addAttribute("empty", "비밀번호가 비어있습니다");
            redirectAttributes.addAttribute("id", id);
            return "redirect:/members/{id}/validate";
        }

        if (isValidatedHuman(id, password)) {
            redirectAttributes.addAttribute("mismatch", "비밀번호가 잘못됐습니다");
            redirectAttributes.addAttribute("id", id);
            return "redirect:/members/{id}/validate";
        }

        return "redirect:/login";
    }

    private boolean isValidatedHuman(Long id, String password) {
        return !memberService.findOne(id).getPassword().equals(password);
    }


    @PostMapping("/signUp/mail")
    @ResponseBody
    public boolean Mail(String email, HttpServletRequest request) {

        boolean isDup = false;

        Member byEmail = memberService.findByEmail(email);

        if (byEmail == null && !redisTemplate.hasKey(email)) {

            String code = emailService.mailCheck(email);

            redisTemplate.opsForValue().set(email, code, Duration.ofMinutes(3));

            String.valueOf(redisTemplate.opsForValue().get(email));

        } else {
            isDup = true;
        }

//
//        HttpSession session = request.getSession();
//        session.setAttribute("email", code);

        return isDup;
    }

    @PostMapping("/signUp/mail/check")
    @ResponseBody
    public boolean codeCheck(String code, HttpServletRequest request) {

        boolean isCheck = false;

        HttpSession session = request.getSession();

        if (code.equals(session.getAttribute("email"))) {
            isCheck = true;
        }

        return isCheck;
    }


    @PostMapping("/signUp/duplicateCheck")
    @ResponseBody
    public Boolean duplicateCheck(@RequestParam String userId) {
        Boolean isDuplicated = false;

        if (memberService.findByUserId(userId) != null) {
            isDuplicated = true;
        }
        return isDuplicated;
    }


    @PostMapping("/add/duplicateCheckByEmail")
    public Boolean duplicateCheckByEmail(@RequestParam String email) {
        Boolean isDuplicated = false;

        if (memberService.findByEmail(email) != null) {
            isDuplicated = true;
        }
        return isDuplicated;
    }

    private void makeUpdateCount() {
        if (!redisTemplate.hasKey("signUpPerDay")) {
            ValueOperations valueOperations = redisTemplate.opsForValue();
            valueOperations.set("signUpPerDay", 0L);
            valueOperations.increment("signUpPerDay");
        } else {
            redisTemplate.opsForValue().increment("signUpPerDay");
        }


    }
}