app.config ['$stateProvider', '$urlRouterProvider',
	($stateProvider, $urlRouterProvider) ->
		profile = [
			'$q', '$log', '$state', 'AuthProvider', 'AccountService', 'DataService',
			($q, $log, $state, AuthProvider, AccountService, DataService)->
				deferred = $q.defer()

				getUserProfile = ()->
					$log.debug 'Getting user profile from the server...'
					AccountService.getUserProfile()
						.then (data)->
							DataService.setUser(data)

							$log.debug 'Got user profile from the server...'
							deferred.resolve()

				$log.debug 'Resolving user profile..'
				authenticated = AuthProvider.isAuthenticated()

				if authenticated && !DataService.user?
					getUserProfile()
				else
					$log.debug 'No need for user profile, user is not authenticated...'
					deferred.resolve()
		]

		$urlRouterProvider.otherwise('/systems')

		states =
			'app':
				url: ''
				templateUrl: 'assets/views/index.html'
				controller: 'IndexController'
				controllerAs: 'indexCtrl'
				abstract: true
				resolve: profile
			# 'app.main':
			# 	url: '/'
			# 	templateUrl: 'assets/views/main.html'
			'app.register':
				url: '/register'
				templateUrl: 'assets/views/register.html'
			'app.login':
				url: '/login'
				templateUrl: 'assets/views/login.html'
			'app.recover':
				url: '/recover'
				templateUrl: 'assets/views/recover.html'
			#'app.report':
			#	url: '/report'
			#	templateUrl: 'assets/views/report.html'
			'app.tests':
				url: '/tests'
				abstract: true
				templateUrl: 'assets/views/tests/index.html'
			'app.tests.execution':
				url: '/:test_id?systemId&actorId&specId'
				templateUrl: 'assets/views/tests/execution-v2.html' #'assets/views/tests/execution.html'
				#abstract:true
				controller: 'TestExecutionControllerV2' #'TestExecutionController'
				controllerAs: 'testExecutionCtrl'
			#'app.tests.execution.presentation':
				#url: ''
				#templateUrl: 'assets/views/test-presentation.html'
				#controller: 'TestPresentationController'
				#controllerAs: 'testPresentationCtrl'
			'app.reports':
				url: '/reports/:session_id'
				templateUrl: 'assets/views/result.html'
				abstract: true
				controller: 'TestResultController'
				controllerAs: 'testResultCtrl'
			'app.reports.presentation':
				url: ''
				templateUrl: 'assets/views/test-presentation.html'
				controller: 'TestPresentationController'
				controllerAs: 'testPresentationCtrl'
			'app.tutorial':
        url: '/tutorial'
        templateUrl: 'assets/views/tutorial.html'
			'app.systems':
				url: '/systems'
				abstract: true
				templateUrl: 'assets/views/systems/index.html'
			'app.systems.list':
				url: ''
				templateUrl: 'assets/views/systems/list.html'
				controller: 'SystemsController'
				controllerAs: 'systemsCtrl'
			'app.systems.detail':
				url: '/:id'
				templateUrl: 'assets/views/systems/detail.html'
				abstract: true
			'app.systems.detail.info':
				url: ''
				templateUrl: 'assets/views/systems/info.html'
				controller: 'SystemController'
				controllerAs: 'systemCtrl'
			'app.systems.detail.conformance':
				url: '/conformance'
				template: '<div ui-view/>'
				abstract: true
			'app.systems.detail.conformance.list':
				url: ''
				templateUrl: 'assets/views/systems/conformance/index.html'
				controller: 'ConformanceStatementController'
				controllerAs: 'conformanceStatementCtrl'
			'app.systems.detail.conformance.detail':
				url: '/detail/:actor_id?specId'
				templateUrl: 'assets/views/systems/conformance/detail.html'
				controller: 'ConformanceStatementDetailController'
				controllerAs: 'conformanceStatementDetailCtrl'
			'app.systems.detail.conformance.create':
				url: '/create'
				templateUrl: 'assets/views/systems/conformance/create.html'
				controller: 'CreateConformanceStatementController'
				controllerAs: 'createConformanceStatementCtrl'
			'app.systems.detail.tests':
				url: '/tests'
				templateUrl: 'assets/views/systems/tests.html'
				controller: 'SystemTestsController'
				controllerAs: 'systemTestsCtrl'
			'app.systems.detail.team':
				url: '/team'
				templateUrl: 'assets/views/systems/team.html'
				controller: 'SystemTeamController'
				controllerAs: 'systemTeamCtrl'
			'app.page1':
				url: '/page1'
				controller: 'SystemsController'
				controllerAs: 'sc'
				templateUrl: 'assets/views/page1.html'
			'app.page2':
				url: '/page2'
				templateUrl: 'assets/views/page2.html'
			'app.users':
				url: '/users'
				templateUrl: 'assets/views/users.html'
			'app.profile':
				url: '/profile'
				templateUrl: 'assets/views/profile.html'
			'app.settings':
				url: '/settings'
				templateUrl: 'assets/views/settings.html'
			'app.support':
				url: '/support'
				templateUrl: 'assets/views/help.html'
			'app.contact':
				url: '/contact'
				templateUrl: 'assets/views/contact.html'
			'app.about':
				url: '/about'
				templateUrl: 'assets/views/about.html'
			'app.admin':
				url: '/admin'
				templateUrl: 'assets/views/admin/index.html'
				controller: 'AdminController'
			'app.admin.domains':
				url: '/domains'
				abstract: true
				template: '<div ui-view/>'
			'app.admin.domains.list':
				url: ''
				templateUrl: 'assets/views/admin/domains/index.html'
				controller: 'AdminDomainsController'
				controllerAs: 'adminDomainsCtrl'
			'app.admin.domains.create':
				url: '/create'
				templateUrl: 'assets/views/admin/domains/create.html'
				controller: 'CreateDomainController'
				controllerAs: 'createDomainCtrl'
			'app.admin.domains.detail':
				url: '/:id'
				template: '<div ui-view/>'
				abstract: true
			'app.admin.domains.detail.list':
				url: ''
				templateUrl: 'assets/views/admin/domains/detail.html'
				controller: 'DomainDetailsController'
				controllerAs: 'domainDetailsCtrl'
			'app.admin.domains.detail.actors':
				url: '/actors'
				template: '<div ui-view/>'
				abstract: true
			'app.admin.domains.detail.actors.create':
				url: '/create'
				templateUrl: 'assets/views/admin/domains/create-actor.html'
				controller: 'CreateActorController'
				controllerAs: 'createActorCtrl'
			'app.admin.domains.detail.actors.detail':
				url: '/:actor_id'
				template: '<div ui-view/>'
				abstract: true
			'app.admin.domains.detail.actors.detail.list':
				url: ''
				templateUrl: 'assets/views/admin/domains/detail-actor.html'
				controller: 'ActorDetailsController'
				controllerAs: 'actorDetailsCtrl'
			'app.admin.domains.detail.actors.detail.endpoints':
				url: '/endpoints'
				template: '<div ui-view/>'
				abstract: true
			'app.admin.domains.detail.actors.detail.endpoints.create':
				url: '/create'
				templateUrl: 'assets/views/admin/domains/create-endpoint.html'
				controller: 'CreateEndpointController'
				controllerAs: 'createEndpointCtrl'
			'app.admin.domains.detail.actors.detail.endpoints.detail':
				url: '/:endpoint_id'
				templateUrl: 'assets/views/admin/domains/detail-endpoint.html'
				controller: 'EndpointDetailsController'
				controllerAs: 'endpointDetailsCtrl'
			'app.admin.domains.detail.actors.detail.options':
				url: '/options'
				template: '<div ui-view/>'
				abstract: true
			'app.admin.domains.detail.actors.detail.options.create':
				url: '/create'
				templateUrl: 'assets/views/admin/domains/create-option.html'
				controller: 'CreateOptionController'
				controllerAs: 'createOptionCtrl'
			'app.admin.domains.detail.actors.detail.options.detail':
				url: '/:option_id'
				templateUrl: 'assets/views/admin/domains/detail-option.html'
				controller: 'OptionDetailsController'
				controllerAs: 'optionDetailsCtrl'
			'app.admin.domains.detail.specifications':
				url: '/specifications'
				template: '<div ui-view/>'
				abstract: true
			'app.admin.domains.detail.specifications.create':
				url: '/create'
				templateUrl: 'assets/views/admin/domains/create-spec.html'
				controller: 'CreateSpecificationController'
				controllerAs: 'createSpecCtrl'
			'app.admin.domains.detail.specifications.detail':
				url: '/{spec_id:[0-9]+}'
				abstract: true
				template: '<div ui-view/>'
			'app.admin.domains.detail.specifications.detail.list':
				url: ''
				templateUrl: 'assets/views/admin/domains/detail-spec.html'
				controller: 'SpecificationDetailsController'
				controllerAs: 'specDetailsCtrl'
			'app.admin.domains.detail.specifications.detail.actors':
				url: '/actors'
				template: '<div ui-view/>'
				abstract: true
			'app.admin.domains.detail.specifications.detail.actors.create':
				url: '/create'
				templateUrl: 'assets/views/admin/domains/create-actor.html'
				controller: 'CreateActorController'
				controllerAs: 'createActorCtrl'
			'app.admin.domains.detail.specifications.detail.actors.detail':
				url: '/:actor_id'
				template: '<div ui-view/>'
				abstract: true
			'app.admin.domains.detail.specifications.detail.actors.detail.list':
				url: ''
				templateUrl: 'assets/views/admin/domains/detail-actor.html'
				controller: 'ActorDetailsController'
				controllerAs: 'actorDetailsCtrl'
			'app.admin.suites':
				url: '/suites'
				abstract: true
				template: '<div ui-view/>'
			'app.admin.suites.list':
				url: ''
				templateUrl: 'assets/views/admin/suites/index.html'
				controller: 'AdminTestSuitesController'
				controllerAs: 'adminTestSuitesCtrl'
			'app.admin.suites.detail':
				url: '/:id'
				templateUrl: 'assets/views/admin/suites/detail.html'
				controller: 'AdminTestSuiteDetailsController'
				controllerAs: 'adminTestSuiteDetailsCtrl'

		for state, value of states
			$stateProvider.state state, value
		return
]

app.run ['$log', '$rootScope', '$state', 'AuthProvider',
	($log, $rootScope, $state, AuthProvider) ->

		startsWith = (str, prefix) ->
			(str.indexOf prefix) == 0

		requiresLogin = (state) ->
			(state.name == 'app.settings') or
			(state.name == 'app.profile') or
			(state.name == 'app.users') or
			(startsWith state.name, 'app.tests') or
			(startsWith state.name, 'app.reports') or
			(startsWith state.name, 'app.systems') or
			(startsWith state.name, 'app.page') or
			(startsWith state.name, 'app.admin')

		$rootScope.$on '$stateChangeStart', (event, toState, toParams, fromState, fromParams)->
			authenticated = AuthProvider.isAuthenticated()

			$log.debug 'Starting state', toState

			if (requiresLogin toState) and not authenticated
				$log.debug 'State requires login, redirecting...'
				event.preventDefault()
				$state.go 'app.login'
		return
]
