$(function () {

    // init feather icons
    feather.replace();

    // init tooltip & popovers
    $('#navbarButton').click(function() {
      var target = $('#navbarCollapse')
      target.toggle(400)
    })

    //page scroll
    $('a.page-scroll').bind('click', function (event) {
        var $anchor = $(this);
        $('html, body').stop().animate({
            scrollTop: $($anchor.attr('href')).offset().top - 70
        }, 1000);
        event.preventDefault();
    });

    //toggle scroll menu
    $(window).scroll(function () {
        var scroll = $(window).scrollTop();
        //adjust menu background
        if (scroll >= 400) {
            $('.sticky-navbar').addClass('visible');
        } else {
            $('.sticky-navbar').removeClass('visible');
        }

        // adjust scroll to top
        if (scroll >= 600) {
            $('.scroll-top').addClass('active');
        } else {
            $('.scroll-top').removeClass('active');
        }
        return false;
    });

    // scroll top top
    $('.scroll-top').click(function () {
        $('html, body').stop().animate({
            scrollTop: 0
        }, 1000);
    });

    $('#button_email').click(function (e) {
      e.preventDefault()
      var input = $('input[type="email"]')
      $.get('/email/'+input.val())
      input.val("")
    })
});
